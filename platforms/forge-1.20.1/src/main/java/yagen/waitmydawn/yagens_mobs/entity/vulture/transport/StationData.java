package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class StationData extends SavedData {
    public static final int FUEL_CAPACITY = 1024 * 64;
    private static final String STORAGE_ID = "yagens_mobs_vulture_stations";
    private final Map<BlockPos, Post> posts = new HashMap<>();
    private final Map<BlockPos, Staged> occupied = new HashMap<>();
    private final Map<BlockPos, VultureFlight> reserved = new HashMap<>();
    private final Map<UUID, VultureFlight> flights = new LinkedHashMap<>();
    private final Map<UUID, List<Notice>> notices = new HashMap<>();

    public static StationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(tag, level.registryAccess()), StationData::new, STORAGE_ID);
    }

    public static void registerTickets() {
        ForgeChunkManager.setForcedChunkLoadingCallback(yagen.waitmydawn.yagens_mobs.core.ModIdentity.ID,
                (level, tickets) -> List.copyOf(tickets.getEntityTickets().keySet()).forEach(tickets::removeAllTickets));
    }

    boolean canJoin(BlockPos pos, UUID owner, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        Post seed = new Post(owner, axis);
        long combined = 0;
        for (Direction side : List.of(along, along.getOpposite())) {
            for (BlockPos at = pos.relative(side); matches(at, seed); at = at.relative(side)) {
                combined += posts.get(at).fuel;
            }
        }
        return combined <= FUEL_CAPACITY;
    }

    public void addPost(ServerLevel level, BlockPos pos, UUID owner, Direction.Axis axis) {
        BlockPos key = pos.immutable();
        if (posts.containsKey(key)) {
            return;
        }
        var post = new Post(owner, axis);
        posts.put(key, post);
        var group = station(key);
        if (group != null && group.columns().size() > 1) {
            var existing = group.columns().stream().filter(cell -> !cell.equals(key)).findFirst().orElseThrow();
            var settings = posts.get(existing);
            configure(group, settings.name, settings.publicListing, settings.returnBirds);
        }
        refreshPost(level, key);
        refreshAdjacentPosts(level, key, axis);
        setDirty();
    }

    public void removePost(ServerLevel level, BlockPos pos) {
        var post = posts.remove(pos);
        if (post == null) {
            return;
        }
        post.mail.forEach(bag -> MailBag.drop(level, Vec3.atCenterOf(pos.above(2)), bag));
        var staged = occupied.get(pos);
        if (staged != null) {
            unstage(level, staged);
            recover(level, post.owner, pos, List.of(staged.cargo()));
        }
        var flight = reserved.get(pos);
        if (flight != null) {
            flight.failAtCurrent();
            releaseReservations(flight);
        }
        int remaining = post.fuel;
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, post.axis);
        for (Direction side : List.of(along.getOpposite(), along)) {
            BlockPos neighbor = pos.relative(side);
            if (remaining > 0 && matches(neighbor, post)) {
                remaining -= addFuel(station(neighbor), remaining);
            }
        }
        for (int count = remaining; count > 0; count -= 64) {
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.ROTTEN_FLESH, Math.min(count, 64)));
        }
        refreshAdjacentPosts(level, pos, post.axis);
        setDirty();
    }

    private void refreshAdjacentPosts(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        refreshPost(level, pos.relative(along));
        refreshPost(level, pos.relative(along.getOpposite()));
    }

    void refreshPost(ServerLevel level, BlockPos pos) {
        Post post = posts.get(pos);
        if (post == null || !level.hasChunkAt(pos)) {
            return;
        }
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, post.axis);
        boolean start = !matches(pos.relative(along.getOpposite()), post);
        boolean end = !matches(pos.relative(along), post);
        for (BlockPos part : List.of(pos, pos.above())) {
            var before = level.getBlockState(part);
            if (before.is(ModBlocks.VULTURE_POST.get()) && before.getValue(VulturePostBlock.AXIS) == post.axis) {
                var after = before.setValue(VulturePostBlock.START, start).setValue(VulturePostBlock.END, end);
                if (after != before) {
                    level.setBlock(part, after, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public @Nullable Station station(BlockPos pos) {
        Post seed = posts.get(pos);
        if (seed == null) {
            return null;
        }
        Direction forward = seed.axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos first = pos;
        while (matches(first.relative(forward.getOpposite()), seed)) {
            first = first.relative(forward.getOpposite());
        }
        var columns = new ArrayList<BlockPos>();
        for (BlockPos at = first; matches(at, seed); at = at.relative(forward)) {
            columns.add(at);
        }
        var settings = posts.get(first);
        return new Station(List.copyOf(columns), seed.owner, settings.name, settings.publicListing, settings.returnBirds, seed.axis);
    }

    private boolean matches(BlockPos at, Post seed) {
        var next = posts.get(at);
        return next != null && next.axis == seed.axis && next.owner.equals(seed.owner);
    }

    public List<Station> visibleStations(ServerPlayer player, Station source) {
        var seen = new java.util.HashSet<BlockPos>();
        var result = new ArrayList<Station>();
        for (var key : posts.keySet()) {
            if (source.columns().contains(key) || seen.contains(key)) {
                continue;
            }
            var station = station(key);
            seen.addAll(station.columns());
            if (station.publicListing() || station.isOwnedBy(player)) {
                result.add(station);
            }
        }
        result.sort(Comparator.comparingDouble(candidate -> candidate.center().distanceToSqr(source.center())));
        return result;
    }

    public void configure(Station station, String name, boolean publicListing, boolean returnBirds) {
        String normalized = name.strip();
        String limited = normalized.substring(0, Math.min(40, normalized.length()));
        for (BlockPos pos : station.columns()) {
            var post = posts.get(pos);
            post.name = limited;
            post.publicListing = publicListing;
            post.returnBirds = returnBirds;
        }
        setDirty();
    }

    public int fuel(Station station) {
        return station.columns().stream().mapToInt(pos -> posts.get(pos).fuel).sum();
    }

    public int addFuel(Station station, int count) {
        int moved = Math.max(0, Math.min(count, FUEL_CAPACITY - fuel(station)));
        if (moved > 0) {
            posts.get(station.anchor()).fuel += moved;
            setDirty();
        }
        return moved;
    }

    public boolean spendFuel(Station station, int count) {
        if (count < 0 || fuel(station) < count) {
            return false;
        }
        for (BlockPos pos : station.columns()) {
            var post = posts.get(pos);
            int moved = Math.min(count, post.fuel);
            post.fuel -= moved;
            count -= moved;
        }
        setDirty();
        return true;
    }

    public boolean offerPerch(ServerLevel level, Vulture bird) {
        return offerPerch(level, bird, null);
    }

    boolean offerPerch(ServerLevel level, Vulture bird, @Nullable Station destination) {
        return offerPerch(level, bird, destination, false);
    }

    boolean offerPerch(ServerLevel level, Vulture bird, @Nullable Station destination, boolean droppingMail) {
        var candidates = destination == null ? posts.keySet() : destination.columns();
        var choice = candidates.stream()
                .filter(pos -> posts.get(pos).guest == null && bird.distanceToSqr(perchPoint(pos)) <= 32 * 32)
                .filter(pos -> level.hasChunkAt(pos) && (droppingMail || fuel(destination == null ? station(pos) : destination) > 0))
                .filter(pos -> level.getBlockState(pos.above(2)).isAir())
                .min(Comparator.comparingDouble(pos -> bird.distanceToSqr(perchPoint(pos))));
        if (choice.isEmpty()) {
            return false;
        }
        var seat = choice.get();
        posts.get(seat).guest = bird.getUUID();
        bird.claimPerch(seat);
        if (droppingMail) {
            bird.allowMailRest(level.getGameTime());
        }
        setDirty();
        return true;
    }

    public boolean ownsPerch(BlockPos pos, UUID guest) {
        var post = posts.get(pos);
        return post != null && guest.equals(post.guest);
    }

    public void releasePerch(BlockPos pos, UUID guest) {
        if (ownsPerch(pos, guest)) {
            posts.get(pos).guest = null;
            setDirty();
        }
    }

    public static Vec3 perchPoint(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos.above()).add(0.0, VulturePostBlock.BEAM_TOP, 0.0);
    }

    public List<Vulture> perchedBirds(ServerLevel level, Station station) {
        var result = new ArrayList<Vulture>();
        for (BlockPos pos : station.columns()) {
            UUID id = posts.get(pos).guest;
            if (id != null && level.getEntity(id) instanceof Vulture bird && bird.isReadyToCarry(pos)) {
                result.add(bird);
            }
        }
        return result;
    }

    @Nullable UUID postId(BlockPos column) {
        var post = posts.get(column);
        return post == null ? null : post.id;
    }

    @Nullable String postName(BlockPos column, UUID id) {
        var post = posts.get(column);
        return post != null && post.id.equals(id) ? post.name : null;
    }

    void startMail(ServerLevel level, VultureFlight flight) {
        flights.put(flight.id(), flight);
        flight.bind(level);
        setDirty();
    }

    void receiveMail(BlockPos column, ItemStack bag) {
        posts.get(column).mail.add(bag);
        setDirty();
    }

    int mailCount(Station station) {
        return station.columns().stream().mapToInt(pos -> posts.get(pos).mail.size()).sum();
    }

    ItemStack takeMail(Station station) {
        for (BlockPos pos : station.columns()) {
            var mail = posts.get(pos).mail;
            if (!mail.isEmpty()) {
                setDirty();
                return mail.remove(0);
            }
        }
        return ItemStack.EMPTY;
    }

    public List<BlockPos> freeColumns(Station station) {
        return station.columns().stream().filter(pos -> !occupied.containsKey(pos) && !reserved.containsKey(pos)).toList();
    }

    public Map<Component, Integer> reservations(Station station) {
        Map<Component, Integer> result = new LinkedHashMap<>();
        for (BlockPos pos : station.columns()) {
            var flight = reserved.get(pos);
            if (flight != null && flight.reservesSlots()) {
                result.merge(flight.sourceName(), 1, Integer::sum);
            }
        }
        return result;
    }

    public @Nullable BlockState displayCargo(BlockPos pos) {
        var staged = occupied.get(pos);
        if (staged == null) {
            return null;
        }
        int index = staged.columns().indexOf(pos);
        BlockState state = staged.cargo().parts().get(index).state();
        int mate = staged.cargo().chestMate(index);
        if (mate < 0) {
            return state.getBlock() instanceof ChestBlock ? state.setValue(ChestBlock.TYPE, ChestType.SINGLE) : state;
        }
        BlockPos other = staged.columns().get(mate);
        if (pos.distManhattan(other) != 1 || pos.getY() != other.getY()) {
            // Saved shipments from before contiguous reservations may have separated halves.
            return state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }
        return Cargo.faceChestPartner(state, Direction.fromDelta(other.getX() - pos.getX(), 0, other.getZ() - pos.getZ()));
    }

    public boolean isReserved(BlockPos pos) {
        return reserved.containsKey(pos);
    }

    public @Nullable Cargo cargo(BlockPos pos) {
        var staged = occupied.get(pos);
        return staged == null ? null : staged.cargo();
    }

    public boolean stage(ServerLevel level, Station station, BlockPos preferred, Cargo cargo) {
        List<BlockPos> slots = allocate(station, List.of(cargo), preferred);
        if (slots == null || slots.isEmpty()) {
            return false;
        }
        stage(level, new Staged(cargo, slots));
        return true;
    }

    boolean canFit(Station origin, Station destination) {
        return allocate(destination, manifest(origin).stream().map(Staged::cargo).toList(), null) != null;
    }

    private @Nullable List<BlockPos> allocate(Station station, List<Cargo> loads, @Nullable BlockPos preferred) {
        var free = freeColumns(station);
        int size = loads.stream().mapToInt(Cargo::size).sum();
        if (size > free.size() || preferred != null && !free.contains(preferred)) {
            return null;
        }
        var plan = allocateSlots(loads, size, free, preferred, null);
        if (preferred == null || plan != null && plan.contains(preferred)) {
            return plan;
        }
        for (BlockPos pos : free) {
            if (pos.distManhattan(preferred) == 1) {
                plan = allocateSlots(loads, size, free, preferred, pos);
                if (plan != null) {
                    return plan;
                }
            }
        }
        return null;
    }

    private static @Nullable List<BlockPos> allocateSlots(List<Cargo> loads, int size, List<BlockPos> available,
                                                          @Nullable BlockPos preferred, @Nullable BlockPos forcedMate) {
        var free = new ArrayList<>(available);
        if (forcedMate != null) {
            free.remove(preferred);
            free.remove(forcedMate);
        }
        var slots = new BlockPos[size];
        int offset = 0;
        for (Cargo load : loads) {
            for (int index = 0; index < load.size(); index++) {
                int mate = load.chestMate(index);
                if (mate > index) {
                    if (forcedMate != null) {
                        slots[offset + index] = preferred;
                        slots[offset + mate] = forcedMate;
                        forcedMate = null;
                        continue;
                    }
                    int pair = firstAdjacent(free);
                    if (pair < 0) {
                        return null;
                    }
                    slots[offset + index] = free.remove(pair);
                    slots[offset + mate] = free.remove(pair);
                }
            }
            offset += load.size();
        }
        if (preferred != null) {
            free.sort(Comparator.comparingDouble(preferred::distSqr));
        }
        int next = 0;
        for (int i = 0; i < size; i++) {
            if (slots[i] == null) {
                slots[i] = free.get(next++);
            }
        }
        return List.of(slots);
    }

    private static int firstAdjacent(List<BlockPos> free) {
        for (int i = 0; i + 1 < free.size(); i++) {
            if (free.get(i).distManhattan(free.get(i + 1)) == 1) {
                return i;
            }
        }
        return -1;
    }

    void stage(ServerLevel level, Staged staged) {
        for (BlockPos pos : staged.columns()) {
            occupied.put(pos, staged);
        }
        staged.columns().forEach(pos -> syncDisplay(level, pos));
        setDirty();
    }

    public void takeCargo(ServerLevel level, BlockPos pos) {
        var staged = occupied.get(pos);
        if (staged != null) {
            unstage(level, staged);
        }
    }

    private void unstage(ServerLevel level, Staged staged) {
        for (BlockPos pos : staged.columns()) {
            occupied.remove(pos);
            syncDisplay(level, pos);
        }
        setDirty();
    }

    private static void syncDisplay(ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos)) {
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.VULTURE_POST.get())) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private List<Staged> manifest(Station station) {
        return station.columns().stream().map(occupied::get).filter(java.util.Objects::nonNull).distinct().toList();
    }

    public int cargoCount(Station station) {
        return manifest(station).stream().mapToInt(staged -> staged.cargo().size()).sum();
    }

    public static int shippingCost(Station origin, Station destination, int birds) {
        return Math.toIntExact((long) Math.ceil(origin.center().distanceTo(destination.center()) / 100.0) * birds);
    }

    public boolean dispatch(ServerLevel level, ServerPlayer sender, Station origin, Station destination) {
        if (!origin.isOwnedBy(sender) || origin.anchor().equals(destination.anchor())
                || !destination.publicListing() && !destination.isOwnedBy(sender)) {
            return false;
        }
        var manifest = manifest(origin);
        int count = manifest.stream().mapToInt(staged -> staged.cargo().size()).sum();
        var birds = perchedBirds(level, origin);
        var loads = manifest.stream().map(Staged::cargo).toList();
        var destinations = allocate(destination, loads, null);
        if (count == 0 || birds.size() < count || destinations == null
                || !spendFuel(origin, shippingCost(origin, destination, count))) {
            return false;
        }
        var flight = VultureFlight.depart(sender.getUUID(), origin, destination, birds.subList(0, count),
                loads, destinations);
        for (BlockPos pos : flight.destinations()) {
            reserved.put(pos, flight);
        }
        for (var cargo : manifest) {
            unstage(level, cargo);
        }
        flights.put(flight.id(), flight);
        flight.bind(level);
        setDirty();
        return true;
    }

    boolean canDeliver(VultureFlight flight) {
        return flight.destinations().stream().allMatch(pos -> posts.containsKey(pos)
                && reserved.get(pos) == flight && !occupied.containsKey(pos));
    }

    void releaseReservations(VultureFlight flight) {
        flight.destinations().forEach(pos -> reserved.remove(pos, flight));
        setDirty();
    }

    public boolean hasFlight(UUID id) {
        return flights.containsKey(id);
    }

    public void birdKilled(UUID flightId, BlockPos landedFrom) {
        var flight = flights.get(flightId);
        if (flight != null) {
            flight.fail(landedFrom);
            releaseReservations(flight);
        }
    }

    void recover(ServerLevel level, UUID owner, BlockPos origin, List<Cargo> cargo) {
        var flight = VultureFlight.recovery(owner, origin, cargo);
        flights.put(flight.id(), flight);
        setDirty();
    }

    void report(ServerLevel level, UUID playerId, BlockPos pos) {
        var player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            sendNotice(player, level.dimension().location().toString(), pos);
        } else {
            notices.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(new Notice(pos));
            setDirty();
        }
    }

    private static void sendNotice(ServerPlayer player, String dimension, BlockPos pos) {
        if (player.serverLevel().dimension().location().toString().equals(dimension)) {
            long distance = Math.round(player.position().distanceTo(Vec3.atCenterOf(pos)));
            player.sendSystemMessage(Component.translatable("message.yagens_mobs.cargo_dropped",
                    pos.getX(), pos.getY(), pos.getZ(), distance));
        } else {
            player.sendSystemMessage(Component.translatable("message.yagens_mobs.cargo_other_dimension",
                    pos.getX(), pos.getY(), pos.getZ(), dimension));
        }
    }

    public void tick(ServerLevel level) {
        for (var iterator = flights.values().iterator(); iterator.hasNext(); ) {
            var flight = iterator.next();
            if (flight.tick(level, this)) {
                flight.close(level, this);
                iterator.remove();
            }
            setDirty();
        }
        for (var iterator = notices.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().forEach(notice -> sendNotice(player, level.dimension().location().toString(), notice.pos()));
                iterator.remove();
                setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var columns = new ListTag();
        posts.forEach((pos, post) -> {
            var entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.putUUID("Owner", post.owner);
            entry.putUUID("Id", post.id);
            entry.putString("Axis", post.axis.getName());
            entry.putString("Name", post.name);
            entry.putBoolean("Public", post.publicListing);
            entry.putBoolean("Return", post.returnBirds);
            entry.putInt("Fuel", post.fuel);
            if (post.guest != null) {
                entry.putUUID("Guest", post.guest);
            }
            if (!post.mail.isEmpty()) {
                var parcels = new ListTag();
                post.mail.forEach(bag -> parcels.add(bag.save(new CompoundTag())));
                entry.put("Mail", parcels);
            }
            columns.add(entry);
        });
        tag.put("Posts", columns);
        var cargo = new ListTag();
        occupied.values().stream().distinct().forEach(staged -> {
            var entry = staged.cargo().save();
            entry.putLongArray("Columns", staged.columns().stream().mapToLong(BlockPos::asLong).toArray());
            cargo.add(entry);
        });
        tag.put("Cargo", cargo);
        var jobs = new ListTag();
        flights.values().forEach(flight -> jobs.add(flight.save()));
        tag.put("Flights", jobs);
        var messages = new ListTag();
        notices.forEach((player, entries) -> entries.forEach(notice -> {
            var entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putLong("Pos", notice.pos().asLong());
            messages.add(entry);
        }));
        tag.put("Notices", messages);
        return tag;
    }

    private static StationData load(CompoundTag tag, HolderLookup.Provider lookup) {
        var result = new StationData();
        for (Tag value : tag.getList("Posts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var axis = "z".equals(entry.getString("Axis")) ? Direction.Axis.Z : Direction.Axis.X;
            var post = new Post(entry.getUUID("Owner"), axis);
            if (entry.hasUUID("Id")) {
                post.id = entry.getUUID("Id");
            } else {
                result.setDirty();
            }
            for (Tag parcel : entry.getList("Mail", Tag.TAG_COMPOUND)) {
                var bag = ItemStack.of((CompoundTag) parcel);
                if (!bag.isEmpty()) {
                    post.mail.add(bag);
                }
            }
            post.name = entry.getString("Name");
            post.publicListing = entry.getBoolean("Public");
            post.returnBirds = entry.getBoolean("Return");
            post.fuel = Math.max(0, Math.min(FUEL_CAPACITY, entry.getInt("Fuel")));
            post.guest = entry.hasUUID("Guest") ? entry.getUUID("Guest") : null;
            result.posts.put(BlockPos.of(entry.getLong("Pos")), post);
        }
        for (Tag value : tag.getList("Cargo", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var columns = java.util.Arrays.stream(entry.getLongArray("Columns")).mapToObj(BlockPos::of).toList();
            var cargo = Cargo.load(entry, lookup);
            if (columns.size() == cargo.size()) {
                var staged = new Staged(cargo, columns);
                columns.forEach(pos -> result.occupied.put(pos, staged));
            }
        }
        for (Tag value : tag.getList("Flights", Tag.TAG_COMPOUND)) {
            var flight = VultureFlight.load((CompoundTag) value, lookup);
            result.flights.put(flight.id(), flight);
            if (flight.reservesSlots()) {
                flight.destinations().forEach(pos -> result.reserved.put(pos, flight));
            }
        }
        for (Tag value : tag.getList("Notices", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            result.notices.computeIfAbsent(entry.getUUID("Player"), ignored -> new ArrayList<>())
                    .add(new Notice(BlockPos.of(entry.getLong("Pos"))));
        }
        return result;
    }

    private static final class Post {
        private final UUID owner;
        private final Direction.Axis axis;
        private UUID id = UUID.randomUUID();
        private final List<ItemStack> mail = new ArrayList<>();
        private String name = "";
        private boolean publicListing = true;
        private boolean returnBirds;
        private int fuel;
        @Nullable private UUID guest;

        private Post(UUID owner, Direction.Axis axis) {
            this.owner = owner;
            this.axis = axis;
        }
    }

    record Staged(Cargo cargo, List<BlockPos> columns) {}

    private record Notice(BlockPos pos) {}

    public record Station(List<BlockPos> columns, UUID owner, String name, boolean publicListing, boolean returnBirds,
                          Direction.Axis axis) {
        public boolean isOwnedBy(ServerPlayer player) {
            var server = player.serverLevel().getServer();
            // The private world host can manage stations placed under a previous local profile.
            return owner.equals(player.getUUID())
                    || (server.isSingleplayer() && !server.isPublished() && server.isSingleplayerOwner(player.getGameProfile()));
        }

        public BlockPos anchor() {
            return columns.get(0);
        }

        public Vec3 center() {
            return Vec3.atCenterOf(anchor()).lerp(Vec3.atCenterOf(columns.get(columns.size() - 1)), 0.5);
        }

        public Component label() {
            return name.isBlank() ? Component.translatable("block.yagens_mobs.vulture_post") : Component.literal(name);
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.Comparator;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;

final class VultureFlight {
    static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("yagens_mobs", "vulture_flight"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    static void registerTransportTicket() {}
    private static final int TICKING_TICKET_RADIUS = 2;
    private static final double SPEED = 4.0 / 20.0;
    private static final int CRUISE_CLEARANCE = 24;
    private static final int[] TERRAIN_SAMPLES = {0, 2, 4, 8, 16, 24, 32};
    private static final double CLIMB_PER_TICK = .15;
    private static final double GLIDE_SINK_PER_TICK = 1.0 / (60 * 20);
    private static final double LIFT_PER_TICK = .10;
    private static final double LIFT_HEIGHT = 2.0;
    private static final int GLIDE_DURATION = 2 * 60 * 20;
    private static final int PROGRESS_INTERVAL = 20;
    private static final int SLOW_SAMPLE_LIMIT = 3;
    private static final double MIN_PROGRESS_SQR = 4.0;
    private final UUID id;
    private final UUID sender;
    private final BlockPos source;
    private final BlockPos target;
    private final String sourceLabel;
    private final List<BlockPos> destinations;
    private final List<Member> members;
    private final List<Cargo> cargo;
    private final List<DisplayLoad> displayLoads;
    private final Set<ChunkPos> tickets = new HashSet<>();
    private final boolean returnBirds;
    private State state;
    private Vec3 center;
    @Nullable private BlockPos fallFrom;
    private int nextDrop;
    private int dropRadius = 2;
    private int stalled;
    private int waitingForBirds;
    private int dockTicks;
    private boolean bound;
    private boolean cruising;
    private int climbTicks;
    private int glideTicks;
    private double liftTarget = Double.NaN;
    private ItemStack mail = ItemStack.EMPTY;
    private List<BlockPos> mailPath = List.of();
    private int mailIndex;
    @Nullable private List<BlockPos> mailApproach;
    private boolean searchAroundPost;
    private final ArrayDeque<Vec3> detour = new ArrayDeque<>();
    private boolean detourPending;
    private Vec3 progressCheckpoint = Vec3.ZERO;
    private int progressTicks;
    private int slowSamples;

    private VultureFlight(UUID id, UUID sender, BlockPos source, BlockPos target, String sourceLabel,
                          List<BlockPos> destinations, List<Member> members, List<Cargo> cargo,
                          boolean returnBirds, State state, Vec3 center) {
        this.id = id;
        this.sender = sender;
        this.source = source;
        this.target = target;
        this.sourceLabel = sourceLabel;
        this.destinations = List.copyOf(destinations);
        this.members = List.copyOf(members);
        this.cargo = new ArrayList<>(cargo);
        this.displayLoads = state == State.OUTBOUND ? arrangeDisplayLoads(cargo) : List.of();
        this.returnBirds = returnBirds;
        this.state = state;
        this.center = center;
    }

    static VultureFlight depart(UUID sender, StationData.Station source, StationData.Station destination,
                                List<Vulture> birds, List<Cargo> cargo, List<BlockPos> destinations) {
        return new VultureFlight(UUID.randomUUID(), sender, source.anchor(), destination.anchor(), source.name(),
                destinations, birds.stream().map(bird -> new Member(bird.getUUID(), bird.position())).toList(),
                cargo, source.returnBirds(), State.OUTBOUND, average(birds));
    }

    static VultureFlight recovery(UUID sender, BlockPos origin, List<Cargo> cargo) {
        var flight = new VultureFlight(UUID.randomUUID(), sender, origin, origin, "", List.of(), List.of(), cargo,
                false, State.FALLING, Vec3.atCenterOf(origin));
        flight.fallFrom = origin.immutable();
        return flight;
    }

    static VultureFlight deliverMail(UUID sender, BlockPos target, Vulture bird, ItemStack mail,
                                     List<BlockPos> route, boolean direct) {
        var flight = new VultureFlight(UUID.randomUUID(), sender, bird.blockPosition(), target, "",
                List.of(), List.of(new Member(bird.getUUID(), bird.position())), List.of(), false,
                direct ? State.MAIL_LANDING : State.MAIL_ESCAPE, bird.position());
        flight.mail = mail;
        flight.mailPath = List.copyOf(route);
        return flight;
    }

    UUID id() {
        return id;
    }

    Component sourceName() {
        Component name = sourceLabel.isBlank() ? Component.translatable("block.yagens_mobs.vulture_post")
                : Component.literal(sourceLabel);
        return name.copy().append(" (" + source.getX() + ", " + source.getY() + ", " + source.getZ() + ")");
    }

    List<BlockPos> destinations() {
        return destinations;
    }

    boolean reservesSlots() {
        return state == State.OUTBOUND && mail.isEmpty();
    }

    private static List<DisplayLoad> arrangeDisplayLoads(List<Cargo> manifest) {
        var result = new ArrayList<DisplayLoad>();
        for (Cargo load : manifest) {
            for (int i = 0; i < load.size(); i++) {
                int mate = load.chestMate(i);
                if (mate == -1) {
                    result.add(new DisplayLoad(load.parts().get(i).state(), Vulture.CargoDisplay.SINGLE));
                } else if (mate > i) {
                    result.add(new DisplayLoad(load.parts().get(i).state(), Vulture.CargoDisplay.DOUBLE_CHEST));
                    result.add(new DisplayLoad(load.parts().get(mate).state(), Vulture.CargoDisplay.PARTNER));
                }
            }
        }
        return List.copyOf(result);
    }

    void bind(ServerLevel level) {
        bound = true;
        for (int i = 0; i < members.size(); i++) {
            if (level.getEntity(members.get(i).id) instanceof Vulture bird) {
                if (mail.isEmpty()) {
                    var load = displayLoads.get(i);
                    bird.beginShipment(id, load.state(), load.display());
                } else {
                    bird.beginMailShipment(id);
                }
            } else {
                bound = false;
            }
        }
    }

    void failAtCurrent() {
        fail(BlockPos.containing(center));
    }

    void fail(BlockPos at) {
        if (state != State.FALLING) {
            state = State.FALLING;
            detour.clear();
            detourPending = false;
            fallFrom = at.immutable();
            nextDrop = 0;
        }
    }

    boolean tick(ServerLevel level, StationData data) {
        if (state == State.FALLING) {
            return mail.isEmpty() ? recoverCargo(level, data) : recoverMail(level, data);
        }
        if (state == State.DOCKING) {
            // Keep remote birds ticking until they reach any perch they claimed when unloading.
            var occupiedChunks = new HashSet<ChunkPos>();
            boolean approaching = false;
            for (Member member : members) {
                if (level.getEntity(member.id) instanceof Vulture bird) {
                    member.position = bird.position();
                    approaching |= bird.isApproachingPerch();
                } else {
                    approaching = true;
                }
                occupiedChunks.add(new ChunkPos(BlockPos.containing(member.position)));
            }
            maintainTickets(level, occupiedChunks);
            return !approaching || ++dockTicks > 200;
        }
        boolean mailing = !mail.isEmpty();
        var home = data.station(state == State.RETURNING ? source : target);
        if (home == null || state == State.OUTBOUND && !data.canDeliver(this)) {
            failAtCurrent();
            return false;
        }
        if (mailing && !target.equals(MailBag.destination(level, data, mail))) {
            failAtCurrent();
            return false;
        }
        if (state == State.MAIL_ESCAPE || state == State.MAIL_LANDING) {
            return !mailing || followMailPath(level, data, home);
        }
        Vec3 finish = mailing ? MailPath.point(mailApproach == null ? target.above(2) : mailApproach.getFirst())
                : home.center().add(0.0, 2.7, 0.0);
        Vec3 horizontalDelta = finish.subtract(center).multiply(1.0, 0.0, 1.0);
        double remaining = horizontalDelta.horizontalDistance();
        Vec3 direction = horizontalDelta.normalize();
        Vec3 lateral = new Vec3(-direction.z, 0.0, direction.x);
        if (lateral.lengthSqr() < 0.5) {
            lateral = home.axis() == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        }
        keepRouteLoaded(level, lateral, direction, remaining);

        List<Vulture> birds = new ArrayList<>(members.size());
        for (Member member : members) {
            if (level.getEntity(member.id) instanceof Vulture bird && bird.isAlive()) {
                birds.add(bird);
            }
        }
        if (birds.size() != members.size()) {
            if (++waitingForBirds > 160) {
                failAtCurrent();
            }
            return false;
        }
        waitingForBirds = 0;
        if (!chunksReady(level)) {
            return false;
        }
        if (!bound && state == State.OUTBOUND) {
            bind(level);
        }

        if (!detour.isEmpty()) {
            followDetour(level, birds);
            return false;
        }
        if (detourPending) {
            detourPending = false;
            var path = CargoDetour.find(level, birds, center, finish, CargoDetour.footprint(birds));
            if (path != null) {
                detour.addAll(path);
                stalled = 0;
            }
            return false;
        }

        if (mailing && mailApproach == null && remaining <= 24) {
            planMailLanding(level, birds.getFirst(), home);
            return false;
        }
        if (center.distanceToSqr(finish) < (mailing ? .003 : .25)) {
            if (mailing) {
                mailPath = mailApproach;
                mailApproach = null;
                mailIndex = 0;
                state = State.MAIL_LANDING;
                return false;
            }
            return arrive(level, data, birds, home);
        }
        double corridor = terrainFloor(level, lateral, direction, remaining);
        if (corridor + CRUISE_CLEARANCE + LIFT_HEIGHT > level.getMaxY() + 1 + 96) {
            failAtCurrent();
            return false;
        }
        FlightStep movement = planStep(finish.y, remaining, corridor);
        double forward = Math.min(remaining, Math.sqrt(SPEED * SPEED - movement.vertical() * movement.vertical()));
        if (remaining > 1 && movement.vertical() > 0 && !canAdvance(level, lateral, direction, forward, movement.vertical())) {
            forward = 0;
        }
        Vec3 guide = center.add(direction.scale(forward)).add(0, movement.vertical(), 0);
        for (int i = 0; i < birds.size(); i++) {
            var bird = birds.get(i);
            Vec3 offset = lateral.scale(formationOffset(i));
            bird.flyCargo(guide.add(offset), SPEED, direction, movement.flapping());
            members.get(i).position = bird.position();
        }
        Vec3 previous = center;
        center = average(birds);
        stalled = center.distanceToSqr(previous) < 0.00002 ? stalled + 1 : 0;
        recordProgress(previous);
        if (!detourPending && stalled > 200) {
            failAtCurrent();
        }
        return false;
    }

    private void recordProgress(Vec3 previous) {
        // Advance the window only on flight ticks with a ready corridor.
        if (progressTicks == 0) {
            progressCheckpoint = previous;
        }
        if (++progressTicks < PROGRESS_INTERVAL) {
            return;
        }
        progressTicks = 0;
        if (center.distanceToSqr(progressCheckpoint) > MIN_PROGRESS_SQR + 1.0e-7) {
            slowSamples = 0;
        } else if (++slowSamples >= SLOW_SAMPLE_LIMIT) {
            slowSamples = 0;
            detourPending = true;
        }
    }

    private void followDetour(ServerLevel level, List<Vulture> birds) {
        Vec3 next = detour.getFirst();
        Vec3 delta = next.subtract(center);
        Vec3 step = delta.lengthSqr() > SPEED * SPEED ? delta.normalize().scale(SPEED) : delta;
        if (!CargoDetour.clear(level, birds.getFirst(), CargoDetour.footprint(birds), step)) {
            detour.clear();
            detourPending = true;
            return;
        }
        boolean flapping = step.y > .025;
        for (int i = 0; i < birds.size(); i++) {
            var bird = birds.get(i);
            // Keeping heading and offsets fixed preserves the geometry checked by the planner.
            bird.flyCargo(bird.position().add(step), SPEED, Vec3.ZERO, flapping);
            members.get(i).position = bird.position();
        }
        Vec3 previous = center;
        center = average(birds);
        if (center.distanceToSqr(next) < 1.0e-6) {
            detour.removeFirst();
            stalled = 0;
        } else if (center.distanceToSqr(previous) < 0.00002) {
            if (++stalled > 60) {
                detour.clear();
            }
        } else {
            stalled = 0;
        }
    }

    private void planMailLanding(ServerLevel level, Vulture bird, StationData.Station home) {
        BlockPos dock = MailPath.dock(level, bird, target, home.axis());
        var route = dock == null ? null : MailPath.find(level, bird, dock, null);
        if (route != null) {
            Collections.reverse(route);
            mailApproach = route;
            searchAroundPost = false;
        } else if (!searchAroundPost) {
            searchAroundPost = true;
        } else {
            failAtCurrent();
        }
    }

    private boolean followMailPath(ServerLevel level, StationData data, StationData.Station home) {
        var needed = new HashSet<ChunkPos>();
        needed.add(new ChunkPos(BlockPos.containing(center)));
        var member = members.getFirst();
        needed.add(new ChunkPos(BlockPos.containing(member.position)));
        // Keep the next turns ready without pinning the entire cave.
        for (int i = mailIndex; i < Math.min(mailIndex + 12, mailPath.size()); i++) {
            needed.add(new ChunkPos(mailPath.get(i)));
        }
        maintainTickets(level, needed);
        if (!chunksReady(level)) {
            return false;
        }
        if (!(level.getEntity(member.id) instanceof Vulture bird) || !bird.isAlive()) {
            if (++waitingForBirds > 160) {
                failAtCurrent();
            }
            return false;
        }
        waitingForBirds = 0;
        if (!bound) {
            bind(level);
        }
        if (mailIndex == mailPath.size()) {
            mailPath = List.of();
            mailIndex = 0;
            if (state == State.MAIL_ESCAPE) {
                state = State.OUTBOUND;
            } else {
                data.receiveMail(target, mail);
                mail = ItemStack.EMPTY;
                bird.finishShipment(id);
                data.offerPerch(level, bird, home, true);
                var recipient = level.getServer().getPlayerList().getPlayer(sender);
                if (recipient != null) {
                    recipient.sendSystemMessage(Component.translatable("message.yagens_mobs.mail.delivered", home.label()));
                }
                state = State.DOCKING;
            }
            return false;
        }
        Vec3 next = MailPath.point(mailPath.get(mailIndex));
        if (!MailPath.clear(level, bird, bird.position(), next)) {
            BlockPos goal = state == State.MAIL_ESCAPE ? null : mailPath.getLast();
            var replacement = MailPath.find(level, bird, bird.blockPosition(), goal);
            if (replacement == null || !MailPath.clear(level, bird, bird.position(), MailPath.point(replacement.getFirst()))) {
                failAtCurrent();
            } else {
                mailPath = replacement;
                mailIndex = 0;
            }
            return false;
        }
        Vec3 direction = next.subtract(bird.position());
        bird.flyCargo(next, SPEED, direction, direction.y > .025 || bird.tickCount % 90 < 18);
        center = member.position = bird.position();
        if (center.distanceToSqr(next) < 1.0e-6) {
            mailIndex++;
            stalled = 0;
        } else if (++stalled > 60) {
            failAtCurrent();
        }
        return false;
    }

    private boolean recoverMail(ServerLevel level, StationData data) {
        BlockPos drop = fallFrom == null ? BlockPos.containing(center) : fallFrom;
        var chunks = new HashSet<ChunkPos>();
        FlightPath.addChunks(chunks, new AABB(drop).inflate(dropRadius, 0, dropRadius));
        maintainTickets(level, chunks);
        if (!chunksReady(level)) {
            return false;
        }
        if (nextDrop > 0) {
            nextDrop--;
            return false;
        }
        BlockPos landing = MailParcelBlock.land(level, drop, mail, dropRadius);
        if (landing == null) {
            dropRadius = Math.min(dropRadius + 2, 10);
            nextDrop = 20;
            return false;
        }
        mail = ItemStack.EMPTY;
        data.report(level, sender, landing);
        return true;
    }

    private FlightStep planStep(double destinationY, double remaining, double corridor) {
        double floor = corridor + CRUISE_CLEARANCE;
        if (remaining <= .35 || cruising && remaining <= 18) {
            double landing = remaining <= .35 ? destinationY : Math.max(corridor + 1.75, destinationY + remaining * 1.15);
            return new FlightStep(Mth.clamp(landing - center.y, -.14, CLIMB_PER_TICK), false);
        }

        if (!cruising) {
            double climb = floor + LIFT_HEIGHT - center.y;
            if (climb > 1.0e-6) {
                boolean flapping = climbTicks++ % 90 < 21;
                return new FlightStep(Math.min(CLIMB_PER_TICK, climb), flapping);
            }
            cruising = true;
        }

        if (Double.isNaN(liftTarget) && (glideTicks >= GLIDE_DURATION || center.y - GLIDE_SINK_PER_TICK < floor - 1.0e-6)) {
            liftTarget = Math.max(center.y + LIFT_HEIGHT, floor + LIFT_HEIGHT);
        }
        if (!Double.isNaN(liftTarget)) {
            double climb = liftTarget - center.y;
            if (climb > 1.0e-6) {
                return new FlightStep(Math.min(LIFT_PER_TICK, climb), true);
            }
            liftTarget = Double.NaN;
            glideTicks = 0;
        }
        glideTicks++;
        return new FlightStep(-GLIDE_SINK_PER_TICK, false);
    }

    private boolean arrive(ServerLevel level, StationData data, List<Vulture> birds, StationData.Station home) {
        if (state == State.OUTBOUND) {
            int cursor = 0;
            for (Cargo load : cargo) {
                data.stage(level, new StationData.Staged(load, destinations.subList(cursor, cursor + load.size())));
                cursor += load.size();
            }
            cargo.clear();
            data.releaseReservations(this);
            birds.forEach(Vulture::clearCarriedBlock);
            if (returnBirds && data.station(source) != null) {
                state = State.RETURNING;
                progressTicks = 0;
                slowSamples = 0;
                cruising = false;
                climbTicks = 0;
                glideTicks = 0;
                liftTarget = Double.NaN;
                return false;
            }
        }
        for (Vulture bird : birds) {
            bird.finishShipment(id);
            data.offerPerch(level, bird, home);
        }
        state = State.DOCKING;
        return false;
    }

    private boolean recoverCargo(ServerLevel level, StationData data) {
        data.releaseReservations(this);
        if (cargo.isEmpty()) {
            return true;
        }
        if (fallFrom == null) {
            fallFrom = BlockPos.containing(center);
        }
        if (!members.isEmpty()) {
            maintainTickets(level, Set.of(new ChunkPos(fallFrom)));
        }
        if (!level.hasChunkAt(fallFrom) || !chunksReady(level)) {
            return false;
        }
        if (nextDrop > 0) {
            nextDrop--;
            return false;
        }
        for (Member member : members) {
            if (level.getEntity(member.id) instanceof Vulture bird) {
                bird.finishShipment(id);
            }
        }
        Cargo first = cargo.getFirst();
        for (BlockPos at : BlockPos.spiralAround(fallFrom, dropRadius, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH)) {
            if (!level.hasChunkAt(at)) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            BlockPos place = new BlockPos(at.getX(), top, at.getZ());
            // Heightmaps include partial blocks such as station beams that cannot support the cargo.
            if (first.hasGroundSupport(level, place) && first.place(level, place, null)) {
                cargo.removeFirst();
                data.report(level, sender, place);
                return cargo.isEmpty();
            }
        }
        // Keep the manifest in SavedData while the landing area is obstructed.
        dropRadius = Math.min(dropRadius + 2, 26);
        nextDrop = 20;
        return false;
    }

    private double terrainFloor(ServerLevel level, Vec3 lateral, Vec3 forward, double remaining) {
        double highest = level.getMinY();
        for (int index = 0; index < members.size(); index++) {
            Vec3 lane = center.add(lateral.scale(formationOffset(index)));
            for (int lead : TERRAIN_SAMPLES) {
                if (lead > remaining) {
                    break;
                }
                Vec3 sample = lane.add(forward.scale(lead));
                // A rising route can clear distant terrain without tracking each heightmap step immediately.
                highest = Math.max(highest, terrainHeight(level, sample) - .75 * lead);
            }
        }
        return highest;
    }

    private static int terrainHeight(ServerLevel level, Vec3 sample) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sample.x), Mth.floor(sample.z));
    }

    private boolean canAdvance(ServerLevel level, Vec3 lateral, Vec3 direction, double forward, double rise) {
        for (int index = 0; index < members.size(); index++) {
            Vec3 lane = center.add(lateral.scale(formationOffset(index)));
            int here = terrainHeight(level, lane);
            int ahead = terrainHeight(level, lane.add(direction.scale(forward + .5)));
            if (ahead > here && center.y + rise < ahead + .5) {
                return false;
            }
        }
        return true;
    }

    private double formationOffset(int index) {
        return index - (members.size() - 1) * 0.5;
    }

    private void keepRouteLoaded(ServerLevel level, Vec3 lateral, Vec3 forward, double remaining) {
        var needed = new HashSet<ChunkPos>();
        for (int i = 0; i < members.size(); i++) {
            Vec3 lane = center.add(lateral.scale(formationOffset(i)));
            needed.add(new ChunkPos(BlockPos.containing(lane)));
            if (remaining > 1) {
                needed.add(new ChunkPos(BlockPos.containing(lane.add(forward.scale(.75)))));
            }
            for (int lead : TERRAIN_SAMPLES) {
                if (lead > remaining) {
                    break;
                }
                needed.add(new ChunkPos(BlockPos.containing(lane.add(forward.scale(lead)))));
            }
            needed.add(new ChunkPos(BlockPos.containing(members.get(i).position)));
        }
        if (!mail.isEmpty() && mailApproach == null && remaining <= 24) {
            ChunkPos postChunk = new ChunkPos(target);
            int radius = searchAroundPost ? 2 : 0;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    needed.add(new ChunkPos(postChunk.x + x, postChunk.z + z));
                }
            }
        }
        if (detourPending || !detour.isEmpty()) {
            AABB formation = new AABB(center, center);
            for (var member : members) {
                formation = formation.minmax(new AABB(member.position, member.position));
            }
            // A rotated double chest can extend beyond its carrier's own chunk.
            formation = formation.inflate(2, 0, 2);
            if (detourPending) {
                int radius = CargoDetour.SEARCH_RADIUS + 3;
                FlightPath.addChunks(needed, formation.inflate(radius, 0, radius));
            } else {
                FlightPath.addChunks(needed, formation.minmax(formation.move(detour.getFirst().subtract(center))));
            }
        }
        maintainTickets(level, needed);
    }

    private void maintainTickets(ServerLevel level, Set<ChunkPos> required) {
        for (ChunkPos chunk : required) {
            if (tickets.add(chunk)) {
                StationData.get(level).retainFlightChunk(level, chunk, TICKING_TICKET_RADIUS);
            }
        }
        for (var iterator = tickets.iterator(); iterator.hasNext(); ) {
            var chunk = iterator.next();
            if (!required.contains(chunk)) {
                StationData.get(level).releaseFlightChunk(level, chunk, TICKING_TICKET_RADIUS);
                iterator.remove();
            }
        }
    }

    private boolean chunksReady(ServerLevel level) {
        return tickets.stream().allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
    }

    void close(ServerLevel level, StationData data) {
        for (Member member : members) {
            if (level.getEntity(member.id) instanceof Vulture bird) {
                bird.finishShipment(id);
            }
        }
        data.releaseReservations(this);
        maintainTickets(level, Set.of());
    }

    private static Vec3 average(List<Vulture> birds) {
        Vec3 position = Vec3.ZERO;
        for (Vulture bird : birds) {
            position = position.add(bird.position());
        }
        return position.scale(1.0 / birds.size());
    }

    CompoundTag save(DynamicOps<Tag> lookup) {
        var tag = new CompoundTag();
        SaveTag.putId(tag, "Id", id);
        SaveTag.putId(tag, "Sender", sender);
        tag.putLong("Source", source.asLong());
        tag.putLong("Target", target.asLong());
        tag.putString("Label", sourceLabel);
        tag.putString("State", state.name());
        tag.putBoolean("Return", returnBirds);
        tag.putBoolean("Cruising", cruising);
        tag.putInt("ClimbTicks", climbTicks % 90);
        tag.putInt("GlideTicks", glideTicks);
        if (!Double.isNaN(liftTarget)) {
            tag.putDouble("LiftTarget", liftTarget);
        }
        tag.putLongArray("Destinations", destinations.stream().mapToLong(BlockPos::asLong).toArray());
        writePosition(tag, center);
        if (fallFrom != null) {
            tag.putLong("FallFrom", fallFrom.asLong());
        }
        if (detourPending) {
            tag.putBoolean("DetourPending", true);
        }
        if (!detour.isEmpty()) {
            var route = new ListTag();
            for (Vec3 waypoint : detour) {
                var point = new CompoundTag();
                writePosition(point, waypoint);
                route.add(point);
            }
            tag.put("Detour", route);
        }
        var loads = new ListTag();
        cargo.forEach(load -> loads.add(load.save()));
        tag.put("Manifest", loads);
        var group = new ListTag();
        for (Member member : members) {
            var entry = new CompoundTag();
            SaveTag.putId(entry, "Bird", member.id);
            writePosition(entry, member.position);
            group.add(entry);
        }
        tag.put("Birds", group);
        if (!mail.isEmpty()) {
            tag.put("Mail", SaveTag.saveItem(lookup, mail));
            tag.putLongArray("MailPath", mailPath.subList(mailIndex, mailPath.size()).stream().mapToLong(BlockPos::asLong).toArray());
            if (mailApproach != null) {
                tag.putLongArray("MailApproach", mailApproach.stream().mapToLong(BlockPos::asLong).toArray());
            }
        }
        return tag;
    }

    static VultureFlight load(CompoundTag tag, DynamicOps<Tag> lookup) {
        var loads = tag.getListOrEmpty("Manifest").stream().map(CompoundTag.class::cast)
                .map(entry -> Cargo.load(entry, lookup)).toList();
        var members = tag.getListOrEmpty("Birds").stream().map(CompoundTag.class::cast)
                .map(entry -> new Member(SaveTag.id(entry, "Bird").orElseThrow(), readPosition(entry))).toList();
        var flight = new VultureFlight(SaveTag.id(tag, "Id").orElseThrow(), SaveTag.id(tag, "Sender").orElseThrow(), BlockPos.of(tag.getLongOr("Source", 0L)),
                BlockPos.of(tag.getLongOr("Target", 0L)), tag.getStringOr("Label", ""),
                Arrays.stream(tag.getLongArray("Destinations").orElseGet(() -> new long[0])).mapToObj(BlockPos::of).toList(), members, loads,
                tag.getBooleanOr("Return", false), State.valueOf(tag.getStringOr("State", "")), readPosition(tag));
        if (tag.contains("FallFrom")) {
            flight.fallFrom = BlockPos.of(tag.getLongOr("FallFrom", 0L));
        }
        flight.cruising = tag.getBooleanOr("Cruising", false);
        flight.climbTicks = Mth.clamp(tag.getIntOr("ClimbTicks", 0), 0, 89);
        flight.glideTicks = Mth.clamp(tag.getIntOr("GlideTicks", 0), 0, GLIDE_DURATION);
        if (tag.contains("LiftTarget")) {
            flight.liftTarget = tag.getDoubleOr("LiftTarget", 0D);
        }
        flight.detourPending = tag.getBooleanOr("DetourPending", false);
        tag.getListOrEmpty("Detour").stream().map(CompoundTag.class::cast)
                .map(VultureFlight::readPosition).forEach(flight.detour::addLast);
        if (tag.contains("Mail")) {
            flight.mail = ItemStack.CODEC.parse(lookup, tag.getCompoundOrEmpty("Mail")).result().orElse(ItemStack.EMPTY);
            flight.mailPath = Arrays.stream(tag.getLongArray("MailPath").orElseGet(() -> new long[0])).mapToObj(BlockPos::of).toList();
            if (tag.contains("MailApproach")) {
                flight.mailApproach = Arrays.stream(tag.getLongArray("MailApproach").orElseGet(() -> new long[0])).mapToObj(BlockPos::of).toList();
            }
        }
        return flight;
    }

    private static void writePosition(CompoundTag tag, Vec3 current) {
        tag.putDouble("X", current.x);
        tag.putDouble("Y", current.y);
        tag.putDouble("Z", current.z);
    }

    private static Vec3 readPosition(CompoundTag tag) {
        return new Vec3(tag.getDoubleOr("X", 0D), tag.getDoubleOr("Y", 0D), tag.getDoubleOr("Z", 0D));
    }

    private enum State { OUTBOUND, RETURNING, FALLING, DOCKING, MAIL_ESCAPE, MAIL_LANDING }

    private record DisplayLoad(BlockState state, Vulture.CargoDisplay display) {}
    private record FlightStep(double vertical, boolean flapping) {}

    private static final class Member {
        private final UUID id;
        private Vec3 position;

        private Member(UUID id, Vec3 position) {
            this.id = id;
            this.position = position;
        }
    }
}

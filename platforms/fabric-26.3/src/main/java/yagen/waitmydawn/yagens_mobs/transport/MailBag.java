package yagen.waitmydawn.yagens_mobs.transport;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.Vulture;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class MailBag extends Item {
    private static final String STATION = "Station";
    private static final String BIRD = "Bird";
    private static final Set<String> SERIALIZED_ITEM_KEYS = Set.of("id", "count", "components", "Slot");

    public MailBag(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    static boolean canStore(ItemStack stack) {
        var pending = new ArrayDeque<ItemStack>();
        pending.push(stack);
        while (!pending.isEmpty()) {
            var item = pending.pop();
            if (item.is(ModItems.MAIL_BAG.get())
                    || containsEncodedBag(item.get(DataComponents.BLOCK_ENTITY_DATA))
                    || containsEncodedBag(item.get(DataComponents.ENTITY_DATA))
                    || containsEncodedBag(item.get(DataComponents.CUSTOM_DATA))) {
                return false;
            }
            var container = item.get(DataComponents.CONTAINER);
            if (container != null) {
                container.nonEmptyItemCopyStream().forEach(pending::push);
            }
            var bundle = item.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                bundle.itemCopies().forEach(pending::push);
            }
            var projectiles = item.get(DataComponents.CHARGED_PROJECTILES);
            if (projectiles != null) {
                projectiles.itemCopies().forEach(pending::push);
            }
        }
        return true;
    }

    private static boolean containsEncodedBag(@Nullable net.minecraft.world.item.component.TypedEntityData<?> data) {
        return data != null && containsEncodedBagTag(data.copyTagWithoutId());
    }

    private static boolean containsEncodedBag(@Nullable CustomData data) {
        return data != null && containsEncodedBagTag(data.copyTag());
    }

    private static boolean containsEncodedBagTag(CompoundTag data) {
        if (data.isEmpty()) {
            return false;
        }
        // Captured block entities retain their serialized inventories inside the tongs' custom data.
        String bagId = BuiltInRegistries.ITEM.getKey(ModItems.MAIL_BAG.get()).toString();
        var pending = new ArrayDeque<Tag>();
        pending.push(data);
        while (!pending.isEmpty()) {
            Tag tag = pending.pop();
            if (tag instanceof CompoundTag entry) {
                if (bagId.equals(entry.getStringOr("id", "")) && SERIALIZED_ITEM_KEYS.containsAll(entry.keySet())) {
                    return true;
                }
                entry.keySet().forEach(key -> pending.push(entry.get(key)));
            } else if (tag instanceof ListTag entries) {
                entries.forEach(pending::push);
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, hand, bag);
        }
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    private static void open(ServerPlayer player, InteractionHand hand, ItemStack bag) {
        refreshStationName(player.level(), bag);
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MailBagMenu(id, inventory, hand, bag),
                Component.translatable("item.yagens_mobs.mail_bag")));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel server, Entity entity, net.minecraft.world.entity.EquipmentSlot equipment) {
        if (entity instanceof ServerPlayer player
                && (server.getGameTime() + player.getId()) % 10 == 0 && refreshStationName(server, stack)) {
            player.getInventory().setChanged();
        }
    }

    static boolean refreshStationName(ServerLevel level, ItemStack stack) {
        if (!stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(STATION)) {
            return false;
        }
        var binding = data(stack).getCompoundOrEmpty(STATION);
        var dimension = Identifier.tryParse(binding.getStringOr("Dimension", ""));
        if (!SaveTag.id(binding, "Id").isPresent() || dimension == null) {
            return false;
        }
        var targetLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (targetLevel == null) {
            return false;
        }
        String name = StationData.get(targetLevel).postName(BlockPos.of(binding.getLongOr("Pos", 0L)), SaveTag.id(binding, "Id").orElseThrow());
        if (name == null || name.equals(binding.getStringOr("Name", ""))) {
            return false;
        }
        binding.putString("Name", name);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(STATION, binding));
        return true;
    }

    static @Nullable String cachedStationName(ItemStack stack) {
        var tag = data(stack);
        return tag.contains(STATION) ? tag.getCompoundOrEmpty(STATION).getStringOr("Name", "") : null;
    }

    static Component stationLabel(String name) {
        return name.isBlank() ? Component.translatable("block.yagens_mobs.vulture_post") : Component.literal(name);
    }

    void onStation(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack bag, BlockPos column) {
        var stations = StationData.get(level);
        var station = stations.station(column);
        if (station == null || !station.isOwnedBy(player)) {
            message(player, "own_station");
            return;
        }
        boolean hasBird = ready(bag);
        if (hasBird && column.equals(destination(level, stations, bag))) {
            open(player, hand, bag);
            return;
        }
        var binding = new CompoundTag();
        binding.putLong("Pos", column.asLong());
        SaveTag.putId(binding, "Id", stations.postId(column));
        binding.putString("Dimension", level.dimension().identifier().toString());
        binding.putString("Name", station.name());
        Vulture bird = hasBird ? null : stations.perchedBirds(level, station).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(Vec3.atCenterOf(column))))
                .orElse(null);
        CompoundTag captured = bird == null ? null : bird.saveForMail();
        CustomData.update(DataComponents.CUSTOM_DATA, bag, tag -> {
            tag.put(STATION, binding);
            if (captured != null) {
                tag.put(BIRD, captured);
            }
        });
        if (bird != null) {
            bird.discard();
        }
        player.getInventory().setChanged();
        message(player, hasBird ? "rebound" : bird == null ? "bound" : "captured", station.label());
    }

    static boolean ready(ItemStack bag) {
        var data = bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(STATION) && data.contains(BIRD);
    }

    static @Nullable BlockPos destination(ServerLevel level, StationData stations, ItemStack bag) {
        var binding = data(bag).getCompoundOrEmpty(STATION);
        if (!SaveTag.id(binding, "Id").isPresent() || !level.dimension().identifier().toString().equals(binding.getStringOr("Dimension", ""))) {
            return null;
        }
        BlockPos pos = BlockPos.of(binding.getLongOr("Pos", 0L));
        return SaveTag.id(binding, "Id").orElseThrow().equals(stations.postId(pos)) ? pos : null;
    }

    static boolean dispatch(ServerPlayer player, ItemStack bag) {
        for (ItemStack item : bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList()) {
            if (!canStore(item)) {
                message(player, "nested");
                return false;
            }
        }
        var level = player.level();
        var stations = StationData.get(level);
        BlockPos to = destination(level, stations, bag);
        var home = to == null ? null : stations.station(to);
        if (home == null || !home.isOwnedBy(player)) {
            message(player, "destination");
            return false;
        }
        var bird = ModEntities.VULTURE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        CompoundTag saved = data(bag).getCompoundOrEmpty(BIRD);
        if (bird == null || !SaveTag.id(saved, "UUID").isPresent()) {
            return false;
        }
        bird.load(SaveTag.input(level.registryAccess(), saved));
        if (!bird.isAlive() || bird.isBaby() || level.getEntity(bird.getUUID()) != null) {
            message(player, "unavailable");
            return false;
        }
        BlockPos from = BlockPos.containing(player.getX(), player.getY() + .5, player.getZ());
        Vec3 launch = MailPath.point(from);
        bird.snapTo(launch.x, launch.y, launch.z, player.getYRot(), 0);
        boolean tryDirectFirst = from.distManhattan(to) <= 24 && !MailPath.sky(level, from);
        List<BlockPos> route = tryDirectFirst ? MailPath.toStation(level, bird, from, to, home.axis()) : null;
        boolean direct = route != null;
        if (route == null) {
            route = MailPath.find(level, bird, from, null);
        }
        if (route == null && !tryDirectFirst) {
            route = MailPath.toStation(level, bird, from, to, home.axis());
            direct = route != null;
        }
        if (route == null) {
            message(player, "no_exit");
            return false;
        }
        ItemStack parcel = bag.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, parcel, tag -> tag.remove(BIRD));
        var flight = VultureFlight.deliverMail(player.getUUID(), to, bird, parcel, route, direct);
        if (!level.addFreshEntity(bird)) {
            message(player, "unavailable");
            return false;
        }
        stations.startMail(level, flight);
        message(player, "sent");
        return true;
    }

    static boolean drop(ServerLevel level, Vec3 at, ItemStack stack) {
        var item = new ItemEntity(level, at.x, at.y, at.z, stack.copy());
        item.setDefaultPickUpDelay();
        item.setUnlimitedLifetime();
        return level.addFreshEntity(item);
    }

    private static CompoundTag data(ItemStack bag) {
        return bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void message(ServerPlayer player, String message, Object... args) {
        player.sendOverlayMessage(Component.translatable("message.yagens_mobs.mail." + message, args));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flags) {
        var data = data(stack);
        if (data.contains(STATION)) {
            var binding = data.getCompoundOrEmpty(STATION);
            var pos = BlockPos.of(binding.getLongOr("Pos", 0L));
            var name = binding.getStringOr("Name", "");
            tooltip.accept(Component.translatable("item.yagens_mobs.mail_bag.bound",
                    stationLabel(name),
                    pos.getX(), pos.getY(), pos.getZ()));
        } else {
            tooltip.accept(Component.translatable("item.yagens_mobs.mail_bag.unbound"));
        }
        if (ready(stack)) {
            tooltip.accept(Component.translatable("item.yagens_mobs.mail_bag.carrying"));
        }
    }
}

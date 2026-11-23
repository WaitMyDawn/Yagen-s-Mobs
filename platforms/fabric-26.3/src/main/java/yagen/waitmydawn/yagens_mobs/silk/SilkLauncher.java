package yagen.waitmydawn.yagens_mobs.silk;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SilkLauncher extends Item {
    private static final String PENDING = "SilkShot";

    private static final String COURIER = "SilkCourier";
    private static final String COURIER_UNTIL = "SilkCourierUntil";

    public interface Courier {
        boolean canRelay();
        default boolean reserveRelay(ServerPlayer player) { return canRelay(); }
        void acceptRelay(Relay relay);
    }

    public record Relay(BlockPos anchor, UUID lineId, UUID courier, RockSilkPath.Pin pin, long deadline) {
        private @Nullable RockSilkLine line(ServerLevel level) {
            return level.getEntity(lineId) instanceof RockSilkLine line && line.relayedBy(courier) ? line : null;
        }

        public boolean alive(ServerLevel level) {
            return level.getGameTime() < deadline && (!level.hasChunkAt(anchor) || line(level) != null);
        }

        public Vec3 approach() {
            return pin.tip().add(Vec3.atLowerCornerOf(pin.face().getUnitVec3i()).scale(1.06));
        }

        public boolean finish(ServerLevel level) {
            RockSilkLine line = line(level);
            if (line == null) {
                return false;
            }
            if (!RockSilkPath.supports(level, pin) || !RockSilkPath.clear(level, line.first().tip(), pin.tip())) {
                line.recover(null);
            } else {
                line.finishRelay(pin);
            }
            return true;
        }

        public void cancel(ServerLevel level) {
            RockSilkLine line = line(level);
            if (line != null) {
                line.recover(null);
            }
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Anchor", anchor.asLong());
            SaveTag.putId(tag, "Line", lineId);
            SaveTag.putId(tag, "Courier", courier);
            tag.put("Pin", pin.save());
            tag.putLong("Expiry", deadline);
            return tag;
        }

        @Nullable public static Relay load(CompoundTag tag) {
            return SaveTag.id(tag, "Line").isPresent() && SaveTag.id(tag, "Courier").isPresent() && tag.contains("Pin")
                    ? new Relay(BlockPos.of(tag.getLongOr("Anchor", 0L)), SaveTag.id(tag, "Line").orElseThrow(), SaveTag.id(tag, "Courier").orElseThrow(),
                            RockSilkPath.Pin.load(tag.getCompoundOrEmpty("Pin")), tag.getLongOr("Expiry", 0L)) : null;
        }
    }

    public static boolean selectCourier(ServerPlayer owner, ItemStack stack, Entity entity) {
        Shot shot = shot(stack);
        if (!(entity instanceof Courier courier) || !courier.canRelay() || shot == null
                || !shot.position().dimension().equals(owner.level().dimension()) || owner.distanceToSqr(entity) > 64
                || !(owner.level().getEntity(shot.line()) instanceof RockSilkLine line)
                || !line.ownedBy(owner) || !line.heldBy(owner) || !courier.reserveRelay(owner)) {
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            SaveTag.putId(tag, COURIER, entity.getUUID());
            tag.putLong(COURIER_UNTIL, owner.level().getGameTime() + 300);
        });
        owner.sendOverlayMessage(Component.translatable("message.yagens_mobs.salvage_ray.relay_ready"));
        return true;
    }

    private static void launchRelay(ServerPlayer owner, ItemStack stack, RockSilkLine line, UUID courierId) {
        ServerLevel level = owner.level();
        Entity entity = level.getEntity(courierId);
        if (!(entity instanceof Courier courier) || !courier.canRelay() || owner.distanceToSqr(entity) > 100) {
            clearCourier(stack);
            owner.sendOverlayMessage(Component.translatable("message.yagens_mobs.salvage_ray.busy"));
            return;
        }
        var aim = RockSilkPath.relayAim(owner, line.first(), available(stack), viewChunks(owner));
        if (aim.status() == RockSilkPath.Status.NO_TARGET) {
            clearCourier(stack);
            feedback(owner, aim.status());
            return;
        }
        if (!aim.canPin() || !level.mayInteract(owner, aim.pin().block())) {
            feedback(owner, aim.canPin() ? RockSilkPath.Status.UNSUPPORTED : aim.status());
            return;
        }
        long expiry = level.getGameTime() + Math.max(400, Math.round(entity.position().distanceTo(aim.pin().tip()) * 7));
        var job = new Relay(line.first().block(), line.getUUID(), courierId, aim.pin(), expiry);
        if (courier.reserveRelay(owner) && line.startRelay(owner, stack, entity, aim.spools(), expiry)) {
            courier.acceptRelay(job);
        }
    }

    public static boolean isSelectedCourier(ServerPlayer owner, Entity courier) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = owner.getItemInHand(hand);
            if (held.getItem() instanceof SilkLauncher) {
                var data = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (selectingCourier(owner.level(), data) && courier.getUUID().equals(SaveTag.id(data, COURIER).orElseThrow())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int viewChunks(Player player) {
        return player instanceof ServerPlayer serverPlayer ? Math.min(serverPlayer.requestedViewDistance(),
                serverPlayer.level().getServer().getPlayerList().getViewDistance()) : 16;
    }

    private static void clearCourier(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(COURIER);
            tag.remove(COURIER_UNTIL);
        });
    }

    private static boolean selectingCourier(Level level, CompoundTag tag) {
        return SaveTag.id(tag, COURIER).isPresent() && level.getGameTime() < tag.getLongOr(COURIER_UNTIL, 0L);
    }

    record Shot(UUID line, GlobalPos position, RockSilkPath.Pin pin, boolean extend) {
    }

    public SilkLauncher(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer owner) {
            operate(owner, hand, stack);
        }
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            operate(player, context.getHand(), stack);
        }
        return (context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    private void operate(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        ServerLevel level = player.level();
        Shot shot = shot(stack);
        RockSilkLine line = null;
        if (shot != null && shot.position().dimension().equals(level.dimension())) {
            if (level.getEntity(shot.line()) instanceof RockSilkLine found && found.pending() && found.ownedBy(player)) {
                line = found;
            } else if (!level.hasChunkAt(shot.position().pos())) {
                feedback(player, RockSilkPath.Status.UNLOADED);
                return;
            }
        }
        if (shot != null && line == null) {
            clear(stack);
        }
        if (player.isShiftKeyDown()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new LauncherMenu(id, inventory, hand, stack),
                    Component.translatable("screen.yagens_mobs.silk_launcher")));
            return;
        }
        var customs = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (line != null && selectingCourier(level, customs)) {
            launchRelay(player, stack, line, SaveTag.id(customs, COURIER).orElseThrow());
            return;
        }
        var aim = preview(player, stack);
        if (line != null && aim.status() == RockSilkPath.Status.HANG) {
            line.hang(player, stack);
            return;
        }
        if (!aim.canPin() || !level.mayInteract(player, aim.pin().block())) {
            feedback(player, aim.canPin() ? RockSilkPath.Status.UNSUPPORTED : aim.status());
            return;
        }
        if (line != null) {
            if (line.connect(player, stack, aim.pin(), aim.spools())) {
                emitShot(level, player, aim.pin().tip());
            }
            return;
        }
        var newLine = ModEntities.ROCK_SILK_LINE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (newLine == null) {
            return;
        }
        newLine.configure(aim.pin(), player, hand);
        var contents = chamber(stack);
        boolean extend = isSpool(contents.getFirst());
        if (!level.addFreshEntity(newLine)) {
            return;
        }
        if (!spend(stack, 1)) {
            newLine.discard();
            return;
        }
        // The first pin already owns a paid spool if the launcher leaves the player's hand.
        setShot(stack, newLine, extend);
        emitShot(level, player, aim.pin().tip());
    }

    private static void emitShot(ServerLevel level, Player player, Vec3 target) {
        Vec3 start = player.getEyePosition().add(0, -0.28, 0);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(target) / 3));
        for (int index = 1; index <= steps; index++) {
            Vec3 point = start.lerp(target, (double) index / steps);
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 2, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, player.getSoundSource(), 0.8F, 1.14F);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, net.minecraft.world.entity.EquipmentSlot equipment) {
        if (!(entity instanceof ServerPlayer player) || !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .contains(PENDING) || player.tickCount % 10 != 0) {
            return;
        }
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (SaveTag.id(data, COURIER).isPresent() && !selectingCourier(level, data)) {
            clearCourier(stack);
        }
        Shot shot = shot(stack);
        if (shot == null || !level.dimension().equals(shot.position().dimension())) {
            clear(stack);
            return;
        }
        if (!level.hasChunkAt(shot.position().pos())) {
            return;
        }
        if (!(player.level().getEntity(shot.line()) instanceof RockSilkLine line)
                || !line.pending() || !line.ownedBy(player)) {
            clear(stack);
        } else if (player.isAlive() && !line.heldBy(player)) {
            line.hang(player, stack);
        }
    }

    public static RockSilkPath.Aim preview(Player player, ItemStack stack) {
        return preview(player, stack, viewChunks(player));
    }

    public static RockSilkPath.Aim preview(Player player, ItemStack stack, int viewChunks) {
        Shot shot = shot(stack);
        RockSilkPath.Pin first = shot != null && shot.position().dimension().equals(player.level().dimension())
                ? shot.pin() : null;
        if (first != null && selectingCourier(player.level(), stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())) {
            return RockSilkPath.relayAim(player, first, available(stack), viewChunks);
        }
        return RockSilkPath.aim(player, first, first == null ? loaded(stack) : available(stack));
    }

    public static @Nullable RockSilkPath.Pin pendingPin(Player player, ItemStack stack) {
        Shot shot = shot(stack);
        return shot != null && shot.position().dimension().equals(player.level().dimension()) ? shot.pin() : null;
    }

    public static int available(ItemStack stack) {
        Shot shot = shot(stack);
        return shot == null ? loaded(stack) : shot.extend() ? 1 + loaded(stack) : 1;
    }

    private static void feedback(Player player, RockSilkPath.Status status) {
        player.sendOverlayMessage(Component.translatable(statusKey(status)));
    }

    public static String statusKey(RockSilkPath.Status status) {
        return "message.yagens_mobs.silk_launcher." + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    static boolean matches(ItemStack stack, RockSilkLine line) {
        Shot shot = shot(stack);
        return shot != null && shot.line().equals(line.getUUID());
    }

    static ItemStack find(Player player, RockSilkLine line) {
        if (matches(player.getOffhandItem(), line)) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (matches(stack, line)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static boolean spend(ItemStack stack, int count) {
        if (count == 0) {
            return true;
        }
        var chamber = chamber(stack);
        int remaining = count;
        for (ItemStack slot : chamber) {
            if (isSpool(slot)) {
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
            }
            if (remaining == 0) {
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(chamber));
                return true;
            }
        }
        return false;
    }

    public static int loaded(ItemStack stack) {
        return chamber(stack).stream().filter(SilkLauncher::isSpool).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean isSpool(ItemStack stack) {
        return stack.is(ModItems.ROCK_SILK_SPOOL.get());
    }

    private static NonNullList<ItemStack> chamber(ItemStack stack) {
        var slots = NonNullList.withSize(LauncherMenu.SIZE, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    private static @Nullable Shot shot(ItemStack stack) {
        var customs = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!stack.is(ModItems.SILK_LAUNCHER.get()) || customs == null || !customs.contains(PENDING)) {
            return null;
        }
        var tag = customs.getCompoundOrEmpty(PENDING);
        if (!SaveTag.id(tag, "Line").isPresent()) {
            return null;
        }
        var dimension = Identifier.tryParse(tag.getStringOr("Dimension", ""));
        if (dimension == null) {
            return null;
        }
        var key = ResourceKey.create(Registries.DIMENSION, dimension);
        var pin = RockSilkPath.Pin.load(tag.getCompoundOrEmpty("First"));
        return new Shot(SaveTag.id(tag, "Line").orElseThrow(), GlobalPos.of(key, pin.block()), pin, tag.getBooleanOr("Extend", false));
    }

    private static void setShot(ItemStack stack, RockSilkLine line, boolean extend) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var entry = new CompoundTag();
            SaveTag.putId(entry, "Line", line.getUUID());
            entry.putString("Dimension", line.level().dimension().identifier().toString());
            entry.put("First", line.first().save());
            entry.putBoolean("Extend", extend);
            tag.put(PENDING, entry);
        });
    }

    static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(PENDING);
            tag.remove(COURIER);
            tag.remove(COURIER_UNTIL);
        });
    }
}


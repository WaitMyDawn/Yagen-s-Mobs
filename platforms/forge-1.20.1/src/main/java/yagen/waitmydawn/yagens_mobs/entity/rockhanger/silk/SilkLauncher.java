package yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.compat.LegacyItemData;
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
            return pin.tip().add(Vec3.atLowerCornerOf(pin.face().getNormal()).scale(1.06));
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
            tag.putUUID("Line", lineId);
            tag.putUUID("Courier", courier);
            tag.put("Pin", pin.save());
            tag.putLong("Expiry", deadline);
            return tag;
        }

        @Nullable public static Relay load(CompoundTag tag) {
            return tag.hasUUID("Line") && tag.hasUUID("Courier") && tag.contains("Pin")
                    ? new Relay(BlockPos.of(tag.getLong("Anchor")), tag.getUUID("Line"), tag.getUUID("Courier"),
                            RockSilkPath.Pin.load(tag.getCompound("Pin")), tag.getLong("Expiry")) : null;
        }
    }

    public static boolean selectCourier(ServerPlayer owner, ItemStack stack, Entity entity) {
        Shot shot = shot(stack);
        if (!(entity instanceof Courier courier) || !courier.canRelay() || shot == null
                || !shot.position().dimension().equals(owner.level().dimension()) || owner.distanceToSqr(entity) > 64
                || !(owner.serverLevel().getEntity(shot.line()) instanceof RockSilkLine line)
                || !line.ownedBy(owner) || !line.heldBy(owner) || !courier.reserveRelay(owner)) {
            return false;
        }
        LegacyItemData.updateCustom(stack, tag -> {
            tag.putUUID(COURIER, entity.getUUID());
            tag.putLong(COURIER_UNTIL, owner.level().getGameTime() + 300);
        });
        owner.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.relay_ready"), true);
        return true;
    }

    private static void launchRelay(ServerPlayer owner, ItemStack stack, RockSilkLine line, UUID courierId) {
        ServerLevel level = owner.serverLevel();
        Entity entity = level.getEntity(courierId);
        if (!(entity instanceof Courier courier) || !courier.canRelay() || owner.distanceToSqr(entity) > 100) {
            clearCourier(stack);
            owner.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.busy"), true);
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
                var data = LegacyItemData.custom(held);
                if (selectingCourier(owner.level(), data) && courier.getUUID().equals(data.getUUID(COURIER))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int viewChunks(Player player) {
        return player instanceof ServerPlayer serverPlayer ? Math.min(yagen.waitmydawn.yagens_mobs.entity.salvage.SalvagePlayerMotion.viewDistance(serverPlayer),
                serverPlayer.serverLevel().getServer().getPlayerList().getViewDistance()) : 16;
    }

    private static void clearCourier(ItemStack stack) {
        LegacyItemData.updateCustom(stack, tag -> {
            tag.remove(COURIER);
            tag.remove(COURIER_UNTIL);
        });
    }

    private static boolean selectingCourier(Level level, CompoundTag tag) {
        return tag.hasUUID(COURIER) && level.getGameTime() < tag.getLong(COURIER_UNTIL);
    }

    record Shot(UUID line, GlobalPos position, RockSilkPath.Pin pin, boolean extend) {
    }

    public SilkLauncher(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer owner) {
            operate(owner, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            operate(player, context.getHand(), stack);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private void operate(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        ServerLevel level = player.serverLevel();
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
        var customs = LegacyItemData.custom(stack);
        if (line != null && selectingCourier(level, customs)) {
            launchRelay(player, stack, line, customs.getUUID(COURIER));
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
        var newLine = ModEntities.ROCK_SILK_LINE.get().create(level);
        if (newLine == null) {
            return;
        }
        newLine.configure(aim.pin(), player, hand);
        var contents = chamber(stack);
        boolean extend = isSpool(contents.get(0));
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || !LegacyItemData.custom(stack)
                .contains(PENDING) || player.tickCount % 10 != 0) {
            return;
        }
        var data = LegacyItemData.custom(stack);
        if (data.hasUUID(COURIER) && !selectingCourier(level, data)) {
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
        if (!(player.serverLevel().getEntity(shot.line()) instanceof RockSilkLine line)
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
        if (first != null && selectingCourier(player.level(), LegacyItemData.custom(stack))) {
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
        player.displayClientMessage(Component.translatable(statusKey(status)), true);
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
        for (ItemStack stack : player.getInventory().items) {
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
                LegacyItemData.saveItems(stack, chamber);
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
        LegacyItemData.loadItems(stack, slots);
        return slots;
    }

    private static @Nullable Shot shot(ItemStack stack) {
        var customs = LegacyItemData.custom(stack);
        if (!stack.is(ModItems.SILK_LAUNCHER.get()) || customs == null || !customs.contains(PENDING)) {
            return null;
        }
        var tag = customs.copy().getCompound(PENDING);
        if (!tag.hasUUID("Line")) {
            return null;
        }
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            return null;
        }
        var key = ResourceKey.create(Registries.DIMENSION, dimension);
        var pin = RockSilkPath.Pin.load(tag.getCompound("First"));
        return new Shot(tag.getUUID("Line"), GlobalPos.of(key, pin.block()), pin, tag.getBoolean("Extend"));
    }

    private static void setShot(ItemStack stack, RockSilkLine line, boolean extend) {
        LegacyItemData.updateCustom(stack, tag -> {
            var entry = new CompoundTag();
            entry.putUUID("Line", line.getUUID());
            entry.putString("Dimension", line.level().dimension().location().toString());
            entry.put("First", line.first().save());
            entry.putBoolean("Extend", extend);
            tag.put(PENDING, entry);
        });
    }

    static void clear(ItemStack stack) {
        LegacyItemData.updateCustom(stack, tag -> {
            tag.remove(PENDING);
            tag.remove(COURIER);
            tag.remove(COURIER_UNTIL);
        });
    }
}


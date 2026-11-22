package yagen.waitmydawn.yagens_mobs.silk;

import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SilkLauncher extends Item {
    private static final String PENDING = "SilkShot";

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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .contains(PENDING) || player.tickCount % 10 != 0) {
            return;
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
        Shot shot = shot(stack);
        RockSilkPath.Pin first = shot != null && shot.position().dimension().equals(player.level().dimension())
                ? shot.pin() : null;
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
        var customs = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(ModItems.SILK_LAUNCHER.get()) || customs == null || !customs.contains(PENDING)) {
            return null;
        }
        var tag = customs.copyTag().getCompound(PENDING);
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var entry = new CompoundTag();
            entry.putUUID("Line", line.getUUID());
            entry.putString("Dimension", line.level().dimension().location().toString());
            entry.put("First", line.first().save());
            entry.putBoolean("Extend", extend);
            tag.put(PENDING, entry);
        });
    }

    static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(PENDING));
    }
}


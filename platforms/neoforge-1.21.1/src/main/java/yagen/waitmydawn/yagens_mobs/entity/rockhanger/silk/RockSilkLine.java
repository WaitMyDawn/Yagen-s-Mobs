package yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class RockSilkLine extends Entity {
    private static final EntityDataAccessor<CompoundTag> GEOMETRY =
            SynchedEntityData.defineId(RockSilkLine.class, EntityDataSerializers.COMPOUND_TAG);

    private int paidSpools = 1;

    public RockSilkLine(EntityType<? extends RockSilkLine> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    void configure(RockSilkPath.Pin first, ServerPlayer owner, InteractionHand hand) {
        var data = new CompoundTag();
        data.put("First", first.save());
        data.putUUID("Owner", owner.getUUID());
        data.putBoolean("OffHand", hand == InteractionHand.OFF_HAND);
        entityData.set(GEOMETRY, data);
        setPos(first.tip());
    }

    public RockSilkPath.Pin first() {
        return RockSilkPath.Pin.load(entityData.get(GEOMETRY).getCompound("First"));
    }

    public @Nullable RockSilkPath.Pin second() {
        var data = entityData.get(GEOMETRY);
        return data.contains("Second", Tag.TAG_COMPOUND)
                ? RockSilkPath.Pin.load(data.getCompound("Second")) : null;
    }

    public boolean pending() {
        return entityData.get(GEOMETRY).hasUUID("Owner");
    }

    boolean ownedBy(Player player) {
        var data = entityData.get(GEOMETRY);
        return data.hasUUID("Owner") && data.getUUID("Owner").equals(player.getUUID());
    }

    boolean heldBy(Player player) {
        InteractionHand hand = entityData.get(GEOMETRY).getBoolean("OffHand")
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return SilkLauncher.matches(player.getItemInHand(hand), this);
    }

    public Vec3 tip() {
        var data = entityData.get(GEOMETRY);
        if (data.contains("Second", Tag.TAG_COMPOUND)) {
            return second().tip();
        }
        if (data.hasUUID("Courier")) {
            return new Vec3(data.getDouble("TipX"), data.getDouble("TipY"), data.getDouble("TipZ"));
        }
        if (data.hasUUID("Owner")) {
            var player = level().getPlayerByUUID(data.getUUID("Owner"));
            if (player != null) {
                Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + (data.getBoolean("OffHand") ? -90 : 90));
                return player.getEyePosition().add(side.scale(0.34)).add(player.getViewVector(1).scale(0.36))
                        .add(0, -0.42, 0);
            }
            return first().tip();
        }
        return new Vec3(first().tip().x, data.getDouble("Bottom"), first().tip().z);
    }

    public boolean supportsGrip() {
        return active() && !pending() && !isRemoved();
    }

    public boolean active() {
        return entityData.get(GEOMETRY).contains("First", Tag.TAG_COMPOUND);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GEOMETRY, new CompoundTag());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !active()) {
            return;
        }
        RockSilkPath.Pin second = second();
        if (invalidSupport(first()) || second != null && invalidSupport(second)) {
            recover(null);
            return;
        }
        if (!pending()) {
            return;
        }
        var data = entityData.get(GEOMETRY);
        if (data.hasUUID("Courier")) {
            Entity courier = level.getEntity(data.getUUID("Courier"));
            if (level.getGameTime() >= data.getLong("RelayDeadline")) {
                recover(null);
            } else if (courier != null && tickCount % 3 == 0) {
                var updated = data.copy();
                writeTip(updated, courier);
                entityData.set(GEOMETRY, updated);
            }
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(data.getUUID("Owner"));
        boolean sameLevel = owner != null && owner.level() == level && owner.isAlive();
        double releaseDistance = RockSilkPath.REACH + 1;
        boolean near = sameLevel && owner.getEyePosition().distanceToSqr(first().tip()) <= releaseDistance * releaseDistance;
        if (near && heldBy(owner)) {
            return;
        }
        ItemStack stack = near ? SilkLauncher.find(owner, this) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            hang(owner, stack);
        } else {
            hang(null, ItemStack.EMPTY);
        }
    }

    private boolean invalidSupport(RockSilkPath.Pin pin) {
        return level().hasChunkAt(pin.block()) && !RockSilkPath.supports(level(), pin);
    }

    boolean relayedBy(UUID courier) {
        var data = entityData.get(GEOMETRY);
        return data.hasUUID("Courier") && data.getUUID("Courier").equals(courier);
    }

    boolean startRelay(ServerPlayer owner, ItemStack stack, Entity courier, int cost, long deadline) {
        if (!pending() || !ownedBy(owner) || !heldBy(owner) || !SilkLauncher.spend(stack, cost - 1)) {
            return false;
        }
        var data = entityData.get(GEOMETRY).copy();
        data.putUUID("Courier", courier.getUUID());
        data.putLong("RelayDeadline", deadline);
        writeTip(data, courier);
        entityData.set(GEOMETRY, data);
        paidSpools = cost;
        SilkLauncher.clear(stack);
        return true;
    }

    private static void writeTip(CompoundTag data, Entity courier) {
        data.putDouble("TipX", courier.getX());
        data.putDouble("TipY", courier.getY() + .11);
        data.putDouble("TipZ", courier.getZ());
    }

    void finishRelay(RockSilkPath.Pin second) {
        var data = entityData.get(GEOMETRY).copy();
        data.remove("Owner");
        data.remove("Courier");
        data.remove("RelayDeadline");
        data.remove("TipX");
        data.remove("TipY");
        data.remove("TipZ");
        data.put("Second", second.save());
        entityData.set(GEOMETRY, data);
        playSound(SoundEvents.CHAIN_PLACE, .8F, 1.2F);
    }

    boolean connect(Player player, ItemStack stack, RockSilkPath.Pin second, int cost) {
        if (!pending() || !ownedBy(player) || !SilkLauncher.matches(stack, this)
                || !SilkLauncher.spend(stack, cost - 1)) {
            return false;
        }
        var data = entityData.get(GEOMETRY).copy();
        data.remove("Owner");
        data.put("Second", second.save());
        entityData.set(GEOMETRY, data);
        paidSpools = cost;
        SilkLauncher.clear(stack);
        playSound(SoundEvents.CHAIN_PLACE, 0.8F, 1.2F);
        return true;
    }

    void hang(@Nullable Player player, ItemStack stack) {
        if (!pending()) {
            return;
        }
        boolean hasLauncher = player != null && SilkLauncher.matches(stack, this);
        int maximum = hasLauncher ? SilkLauncher.available(stack) : 1;
        var drop = RockSilkPath.drop(level(), first(), maximum);
        if (drop.spools() == 0) {
            recover(player);
        } else if (!hasLauncher || SilkLauncher.spend(stack, drop.spools() - 1)) {
            var data = entityData.get(GEOMETRY).copy();
            data.remove("Owner");
            data.putDouble("Bottom", drop.end().y);
            paidSpools = drop.spools();
            entityData.set(GEOMETRY, data);
            CreatureCue.HANGER_HANG.playReplacing(this, 0.7F, SoundEvents.CHAIN_PLACE, 1.3F);
        }
        if (hasLauncher) {
            SilkLauncher.clear(stack);
        }
    }

    @Override
    public boolean isPickable() {
        return active() && !isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!canRecover(player) || player.isShiftKeyDown() && player.getItemInHand(hand).getItem() instanceof SilkLauncher) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            recover(player);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    boolean canRecover(Player player) {
        return active() && !isRemoved() && !player.isSpectator() && (!pending() || ownedBy(player));
    }

    void recover(@Nullable Player player) {
        if (isRemoved()) {
            return;
        }
        ItemStack launcher = player == null ? ItemStack.EMPTY : SilkLauncher.find(player, this);
        for (int left = paidSpools; left > 0; left -= 64) {
            ItemStack returned = new ItemStack(ModItems.ROCK_SILK_SPOOL.get(), Math.min(left, 64));
            if (player == null || !player.getInventory().add(returned)) {
                spawnAtLocation(returned);
            }
        }
        if (!launcher.isEmpty()) {
            SilkLauncher.clear(launcher);
        }
        playSound(SoundEvents.CHAIN_BREAK, 0.65F, 1.25F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(GEOMETRY, tag.getCompound("RockSilk"));
        paidSpools = Math.clamp(tag.getInt("Spools"), 1, LauncherMenu.SIZE * 64);
        if (active()) {
            setPos(first().tip());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("RockSilk", entityData.get(GEOMETRY).copy());
        tag.putInt("Spools", paidSpools);
    }
}


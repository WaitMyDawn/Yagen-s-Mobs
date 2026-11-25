package yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class RockSilkLine extends Entity {
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damage, float amount) {
        return false;
    }

    private static final EntityDataAccessor<String> GEOMETRY =
            SynchedEntityData.defineId(RockSilkLine.class, EntityDataSerializers.STRING);
    private String cachedEncoding;
    private CompoundTag geometry = new CompoundTag();

    private CompoundTag geometry() {
        var encoded = entityData.get(GEOMETRY);
        if (!encoded.equals(cachedEncoding)) {
            cachedEncoding = encoded;
            try {
                geometry = net.minecraft.nbt.TagParser.parseCompoundFully(encoded);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException unavailable) {
                geometry = new CompoundTag();
            }
        }
        return geometry;
    }

    private void setGeometry(CompoundTag tag) {
        cachedEncoding = tag.toString();
        geometry = tag;
        entityData.set(GEOMETRY, cachedEncoding);
    }

    private int paidSpools = 1;

    public RockSilkLine(EntityType<? extends RockSilkLine> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    void configure(RockSilkPath.Pin first, ServerPlayer owner, InteractionHand hand) {
        var data = new CompoundTag();
        data.put("First", first.save());
        SaveTag.putId(data, "Owner", owner.getUUID());
        data.putBoolean("OffHand", hand == InteractionHand.OFF_HAND);
        setGeometry(data);
        setPos(first.tip());
    }

    public RockSilkPath.Pin first() {
        return RockSilkPath.Pin.load(geometry().getCompoundOrEmpty("First"));
    }

    public @Nullable RockSilkPath.Pin second() {
        var data = geometry();
        return data.contains("Second")
                ? RockSilkPath.Pin.load(data.getCompoundOrEmpty("Second")) : null;
    }

    public boolean pending() {
        return SaveTag.id(geometry(), "Owner").isPresent();
    }

    boolean ownedBy(Player player) {
        var data = geometry();
        return SaveTag.id(data, "Owner").isPresent() && SaveTag.id(data, "Owner").orElseThrow().equals(player.getUUID());
    }

    boolean heldBy(Player player) {
        InteractionHand hand = geometry().getBooleanOr("OffHand", false)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return SilkLauncher.matches(player.getItemInHand(hand), this);
    }

    public Vec3 tip() {
        var data = geometry();
        if (data.contains("Second")) {
            return second().tip();
        }
        if (SaveTag.id(data, "Courier").isPresent()) {
            return new Vec3(data.getDoubleOr("TipX", 0), data.getDoubleOr("TipY", 0), data.getDoubleOr("TipZ", 0));
        }
        if (SaveTag.id(data, "Owner").isPresent()) {
            var player = level().getPlayerByUUID(SaveTag.id(data, "Owner").orElseThrow());
            if (player != null) {
                Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + (data.getBooleanOr("OffHand", false) ? -90 : 90));
                return player.getEyePosition().add(side.scale(0.34)).add(player.getViewVector(1).scale(0.36))
                        .add(0, -0.42, 0);
            }
            return first().tip();
        }
        return new Vec3(first().tip().x, data.getDoubleOr("Bottom", 0D), first().tip().z);
    }

    public boolean supportsGrip() {
        return active() && !pending() && !isRemoved();
    }

    public boolean active() {
        return geometry().contains("First");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GEOMETRY, "{}");
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
        var data = geometry();
        if (SaveTag.id(data, "Courier").isPresent()) {
            Entity courier = level.getEntity(SaveTag.id(data, "Courier").orElseThrow());
            if (level.getGameTime() >= data.getLongOr("RelayDeadline", 0L)) {
                recover(null);
            } else if (courier != null && tickCount % 3 == 0) {
                var updated = data.copy();
                writeTip(updated, courier);
                setGeometry(updated);
            }
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(SaveTag.id(data, "Owner").orElseThrow());
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
        var data = geometry();
        return SaveTag.id(data, "Courier").isPresent() && SaveTag.id(data, "Courier").orElseThrow().equals(courier);
    }

    boolean startRelay(ServerPlayer owner, ItemStack stack, Entity courier, int cost, long deadline) {
        if (!pending() || !ownedBy(owner) || !heldBy(owner) || !SilkLauncher.spend(stack, cost - 1)) {
            return false;
        }
        var data = geometry().copy();
        SaveTag.putId(data, "Courier", courier.getUUID());
        data.putLong("RelayDeadline", deadline);
        writeTip(data, courier);
        setGeometry(data);
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
        var data = geometry().copy();
        data.remove("Owner");
        data.remove("Courier");
        data.remove("RelayDeadline");
        data.remove("TipX");
        data.remove("TipY");
        data.remove("TipZ");
        data.put("Second", second.save());
        setGeometry(data);
        playSound(SoundEvents.CHAIN_PLACE, .8F, 1.2F);
    }

    boolean connect(Player player, ItemStack stack, RockSilkPath.Pin second, int cost) {
        if (!pending() || !ownedBy(player) || !SilkLauncher.matches(stack, this)
                || !SilkLauncher.spend(stack, cost - 1)) {
            return false;
        }
        var data = geometry().copy();
        data.remove("Owner");
        data.put("Second", second.save());
        setGeometry(data);
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
            var data = geometry().copy();
            data.remove("Owner");
            data.putDouble("Bottom", drop.end().y);
            paidSpools = drop.spools();
            setGeometry(data);
            playSound(SoundEvents.CHAIN_PLACE, 0.7F, 1.3F);
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
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (!canRecover(player) || player.isShiftKeyDown() && player.getItemInHand(hand).getItem() instanceof SilkLauncher) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            recover(player);
        }
        return (level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
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
                spawnAtLocation((ServerLevel) level(), returned);
            }
        }
        if (!launcher.isEmpty()) {
            SilkLauncher.clear(launcher);
        }
        playSound(SoundEvents.CHAIN_BREAK, 0.65F, 1.25F);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        var tag = SaveTag.read(input);
        setGeometry(tag.getCompoundOrEmpty("RockSilk"));
        paidSpools = Math.clamp(tag.getIntOr("Spools", 0), 1, LauncherMenu.SIZE * 64);
        if (active()) {
            setPos(first().tip());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        var tag = new CompoundTag();
        tag.put("RockSilk", geometry().copy());
        tag.putInt("Spools", paidSpools);
        SaveTag.write(output, tag);
    }
}


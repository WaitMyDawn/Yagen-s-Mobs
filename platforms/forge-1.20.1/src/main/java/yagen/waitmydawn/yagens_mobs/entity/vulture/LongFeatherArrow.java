package yagen.waitmydawn.yagens_mobs.entity.vulture;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class LongFeatherArrow extends AbstractArrow {
    private static final int FLAT_FLIGHT_TICKS = 40;
    private static final EntityDataAccessor<Boolean> FALLING = SynchedEntityData.defineId(
            LongFeatherArrow.class, EntityDataSerializers.BOOLEAN);
    private int flightTicks;

    public LongFeatherArrow(EntityType<? extends LongFeatherArrow> type, Level level) {
        super(type, level);
    }

    LongFeatherArrow(Level level, LivingEntity owner) {
        super(ModEntities.LONG_FEATHER_ARROW.get(), owner, level);
    }

    LongFeatherArrow(Level level, double x, double y, double z) {
        super(ModEntities.LONG_FEATHER_ARROW.get(), x, y, z, level);
        pickup = Pickup.ALLOWED;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FALLING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (entityData.get(FALLING) && !inGround && !isNoPhysics() && !super.isNoGravity()) {
            // Legacy arrows apply a fixed 0.05 gravity in tick; restore half of that step.
            setDeltaMovement(getDeltaMovement().add(0, 0.025, 0));
        }
        if (!level().isClientSide && !inGround && flightTicks < FLAT_FLIGHT_TICKS
                && ++flightTicks == FLAT_FLIGHT_TICKS) {
            entityData.set(FALLING, true);
        }
    }

    @Override
    public boolean isNoGravity() {
        return !entityData.get(FALLING) || super.isNoGravity();
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ModItems.LONG_FEATHER_ARROW.get());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = Math.max(0, Math.min(FLAT_FLIGHT_TICKS, tag.getInt("FlightTicks")));
        entityData.set(FALLING, flightTicks == FLAT_FLIGHT_TICKS);
    }
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

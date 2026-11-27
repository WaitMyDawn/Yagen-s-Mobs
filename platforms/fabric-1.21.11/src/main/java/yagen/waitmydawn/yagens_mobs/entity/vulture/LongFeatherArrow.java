package yagen.waitmydawn.yagens_mobs.entity.vulture;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
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

    LongFeatherArrow(Level level, LivingEntity owner, ItemStack ammo, @Nullable ItemStack weapon) {
        super(ModEntities.LONG_FEATHER_ARROW.get(), owner, level, ammo.copyWithCount(1), weapon);
    }

    LongFeatherArrow(Level level, double x, double y, double z, ItemStack ammo) {
        super(ModEntities.LONG_FEATHER_ARROW.get(), x, y, z, level, ammo.copyWithCount(1), null);
        pickup = Pickup.ALLOWED;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FALLING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !isInGround() && flightTicks < FLAT_FLIGHT_TICKS
                && ++flightTicks == FLAT_FLIGHT_TICKS) {
            entityData.set(FALLING, true);
        }
    }

    @Override
    protected double getDefaultGravity() {
        return entityData.get(FALLING) ? super.getDefaultGravity() * 0.5 : 0;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.LONG_FEATHER_ARROW.get());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = Math.clamp(tag.getIntOr("FlightTicks", 0), 0, FLAT_FLIGHT_TICKS);
        entityData.set(FALLING, flightTicks == FLAT_FLIGHT_TICKS);
    }
}

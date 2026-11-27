package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public final class ShearableBubbleSalamander extends BubbleSalamander {
    private static final EntityDataAccessor<Boolean> SHEARED =
            SynchedEntityData.defineId(ShearableBubbleSalamander.class, EntityDataSerializers.BOOLEAN);
    private static final int REGROWTH_TICKS = 5 * 60 * 20;
    private static final String REGROWTH_KEY = "BladderRegrowth";
    private int regrowthTicks;

    public ShearableBubbleSalamander(EntityType<? extends BubbleSalamander> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHEARED, false);
    }

    @Override
    public boolean hasAirBladder() {
        return !entityData.get(SHEARED);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        var dimensions = super.getDimensions(pose);
        return hasAirBladder() ? dimensions : dimensions.scale(1F, .5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SHEARED.equals(key)) refreshDimensions();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.SHEARS)) return super.mobInteract(player, hand);
        if (isBaby() || !hasAirBladder() || isWorn() || !isAlive() || isTame() && !isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            regrowthTicks = REGROWTH_TICKS;
            entityData.set(SHEARED, true);
            spawnAtLocation(AirBladderBlock.stack(color()));
            playSound(SoundEvents.SHEEP_SHEAR, .8F, 1.12F);
            gameEvent(GameEvent.SHEAR, player);
            stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && regrowthTicks > 0 && --regrowthTicks == 0) {
            entityData.set(SHEARED, false);
        }
    }

    private void readRegrowth(CompoundTag tag) {
        regrowthTicks = net.minecraft.util.Mth.clamp(tag.getInt(REGROWTH_KEY), 0, REGROWTH_TICKS);
        entityData.set(SHEARED, regrowthTicks > 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(REGROWTH_KEY, regrowthTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRegrowth(tag);
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        super.saveToBucketTag(bucket);
        bucket.getOrCreateTag().putInt(REGROWTH_KEY, regrowthTicks);
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        readRegrowth(tag);
    }
}

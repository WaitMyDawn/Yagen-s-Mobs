package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHEARED, false);
    }

    @Override
    public boolean hasAirBladder() {
        return !entityData.get(SHEARED);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        var dimensions = super.getDefaultDimensions(pose);
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
        if (!level().isClientSide()) {
            regrowthTicks = REGROWTH_TICKS;
            entityData.set(SHEARED, true);
            spawnAtLocation((net.minecraft.server.level.ServerLevel)level(), AirBladderBlock.stack(color()));
            playSound(SoundEvents.SHEEP_SHEAR, .8F, 1.12F);
            gameEvent(GameEvent.SHEAR, player);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        return level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && regrowthTicks > 0 && --regrowthTicks == 0) {
            entityData.set(SHEARED, false);
        }
    }

    private void readRegrowth(CompoundTag tag) {
        regrowthTicks = Math.clamp(tag.getIntOr(REGROWTH_KEY, 0), 0, REGROWTH_TICKS);
        entityData.set(SHEARED, regrowthTicks > 0);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(REGROWTH_KEY, regrowthTicks);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        regrowthTicks = Math.clamp(tag.getIntOr(REGROWTH_KEY, 0), 0, REGROWTH_TICKS);
        entityData.set(SHEARED, regrowthTicks > 0);
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        super.saveToBucketTag(bucket);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, tag -> tag.putInt(REGROWTH_KEY, regrowthTicks));
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        readRegrowth(tag);
    }
}

package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import yagen.waitmydawn.yagens_mobs.entity.PetHazards;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;

public final class BubbleSalamander extends TamableAnimal implements Bucketable, GeoEntity {
    @Override
    public void move(MoverType kind, Vec3 motion) {
        if (kind == MoverType.SELF && level() instanceof ServerLevel
                && isTame() && !isPassenger() && !isVehicle() && !isWorn()) {
            Vec3 safeMotion = PetHazards.steer(this, motion, false, false);
            if (safeMotion != motion) {
                motion = safeMotion;
                setDeltaMovement(safeMotion);
            }
        }
        super.move(kind, motion);
    }

    private static final EntityDataAccessor<Integer> WEARER =
            SynchedEntityData.defineId(BubbleSalamander.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SWIM_STROKE =
            SynchedEntityData.defineId(BubbleSalamander.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(BubbleSalamander.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> MIXED_SEED =
            SynchedEntityData.defineId(BubbleSalamander.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> FEMALE =
            SynchedEntityData.defineId(BubbleSalamander.class, EntityDataSerializers.BOOLEAN);
    private static final int FISH_TO_TAME = 3;
    private static final double WEAR_OFFSET = 0.38;
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.bubble_salamander.swim");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.bubble_salamander.crawl");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bubble_salamander.idle");
    private static final RawAnimation WATER_IDLE = RawAnimation.begin().thenLoop("animation.bubble_salamander.water_idle");
    private static final RawAnimation WORN = RawAnimation.begin().thenLoop("animation.bubble_salamander.worn");
    private static final RawAnimation WORN_SWIM = RawAnimation.begin().thenLoop("animation.bubble_salamander.worn_swim");
    private static final RawAnimation ATTACH = RawAnimation.begin().thenPlay("animation.bubble_salamander.attach")
            .thenLoop("animation.bubble_salamander.worn");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private CompoundTag trust = new CompoundTag();
    private boolean fromBucket;
    private int wornTicks;
    @Nullable private Player attachedPlayer;
    @Nullable private Vec3 waitingPosition;

    public BubbleSalamander(EntityType<? extends BubbleSalamander> type, Level level) {
        super(type, level);
        this.moveControl = new WaterSteering();
        setPathfindingMalus(PathType.WATER, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.MOVEMENT_SPEED, .24)
                .add(Attributes.ARMOR, 0).add(Attributes.ARMOR_TOUGHNESS, 0).add(Attributes.ATTACK_DAMAGE, 3);
    }

    public static boolean checkSpawnPlacement(EntityType<BubbleSalamander> type, ServerLevelAccessor level,
                                              MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        if (!ServerConfigs.BUBBLE_SALAMANDER.canSpawn() || !level.getFluidState(pos).is(FluidTags.WATER)
                || !level.getFluidState(pos.above()).is(FluidTags.WATER)
                || !level.getFluidState(pos.above(2)).is(FluidTags.WATER)) {
            return false;
        }
        for (int depth = 3; depth <= 9; depth++) {
            BlockPos above = pos.above(depth);
            if (!level.getFluidState(above).is(FluidTags.WATER)) {
                return level.getBlockState(above).isAir() && level.canSeeSky(above);
            }
        }
        return false;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.BUBBLE_SALAMANDER.maxGroupCount();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group) {
        if (reason != MobSpawnType.BUCKET) {
            setColor(BubbleSalamanderColor.randomCoat(random));
            setFemale(random.nextBoolean());
        }
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    private BubbleSalamanderColor.Coat coat() {
        return new BubbleSalamanderColor.Coat(color(), mixedSeed());
    }

    void setColor(BubbleSalamanderColor.Coat coat) {
        entityData.set(COLOR, coat.color().ordinal());
        entityData.set(MIXED_SEED, coat.color() == BubbleSalamanderColor.MIXED ? coat.seed() : 0L);
    }

    public BubbleSalamanderColor color() {
        return BubbleSalamanderColor.byIndex(entityData.get(COLOR));
    }

    public long mixedSeed() {
        return entityData.get(MIXED_SEED);
    }

    public boolean isFemale() {
        return entityData.get(FEMALE);
    }

    void setFemale(boolean female) {
        entityData.set(FEMALE, female);
    }

    @Override
    public float getAgeScale() {
        return isBaby() ? .56F : 1.0F;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10 : 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WEARER, -1);
        builder.define(SWIM_STROKE, false);
        builder.define(COLOR, BubbleSalamanderColor.GREEN.ordinal());
        builder.define(MIXED_SEED, 0L);
        builder.define(FEMALE, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new PanicGoal(this, 1.28));
        goalSelector.addGoal(1, new SeekWaterGoal());
        goalSelector.addGoal(2, new BreedGoal(this, 1.12));
        goalSelector.addGoal(3, new BubbleSalamanderForaging.HuntFish(this));
        goalSelector.addGoal(4, new FollowSwimmerGoal());
        goalSelector.addGoal(5, new BubbleSalamanderForaging.WaitingMeal(this));
        goalSelector.addGoal(6, new CruiseGoal());
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ModTags.BUBBLE_SALAMANDER_FOOD);
    }

    private boolean canBreedHere() {
        return isTame() && getAge() == 0 && !isWorn() && isInWater()
                && !level().getBiome(blockPosition()).is(ModTags.BUBBLE_SALAMANDER_BREEDING_EXCLUDED);
    }

    @Override
    public boolean canFallInLove() {
        return canBreedHere() && super.canFallInLove();
    }

    @Override
    public boolean canMate(Animal other) {
        return other instanceof BubbleSalamander mate && isFemale() != mate.isFemale()
                && canBreedHere() && mate.canBreedHere()
                && super.canMate(other);
    }

    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal mate) {
        if (canMate(mate)) {
            super.spawnChildFromBreeding(level, mate);
        }
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        if (!(other instanceof BubbleSalamander mate) || isFemale() == mate.isFemale()) {
            return null;
        }
        BubbleSalamander child = ModEntities.BUBBLE_SALAMANDER.get().create(level);
        if (child != null) {
            BubbleSalamander mother = isFemale() ? this : mate;
            BubbleSalamander father = isFemale() ? mate : this;
            child.setColor(BubbleSalamanderColor.Coat.inherit(random, mother.coat(), father.coat()));
            child.setFemale(random.nextBoolean());
            if (getOwnerUUID() != null && Objects.equals(getOwnerUUID(), mate.getOwnerUUID())) {
                child.setOwnerUUID(getOwnerUUID());
                child.setTame(true, false);
                child.setPersistenceRequired();
            }
        }
        return child;
    }

    public boolean isWorn() {
        return entityData.get(WEARER) >= 0;
    }

    public @Nullable Player wearer() {
        if (!level().isClientSide) {
            return attachedPlayer;
        }
        return level().getEntity(entityData.get(WEARER)) instanceof Player player ? player : null;
    }

    public static @Nullable BubbleSalamander wornBy(Player player) {
        return player.level().getEntitiesOfClass(BubbleSalamander.class, player.getBoundingBox().inflate(1.35),
                mob -> mob.isAlive() && mob.wearer() == player).stream().findFirst().orElse(null);
    }

    public static boolean eyeInWater(Player player) {
        Vec3 eye = player.getEyePosition();
        BlockPos pos = BlockPos.containing(eye);
        var fluid = player.level().getFluidState(pos);
        return fluid.is(FluidTags.WATER) && eye.y < pos.getY() + fluid.getHeight(player.level(), pos);
    }

    public static boolean isProvidingAir(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && eyeInWater(player) && wornBy(player) != null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean owner = isOwnedBy(player);
        if (stack.is(Items.WATER_BUCKET) && !isWorn()) {
            if (isTame() && !owner) {
                return InteractionResult.FAIL;
            }
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (isLeashed()) {
                dropLeash(true, true);
            }
            return Bucketable.bucketMobPickup(player, hand, this).orElse(InteractionResult.PASS);
        }
        if (isFood(stack)) {
            boolean acceptsFish = isTame() ? owner : stack.is(Items.COD) || stack.is(Items.SALMON);
            if (!acceptsFish) {
                return InteractionResult.PASS;
            }
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (isTame() && !isBaby() && !canFallInLove() && getHealth() >= getMaxHealth()) {
                return InteractionResult.PASS;
            }
            feed(player, stack);
            return InteractionResult.CONSUME;
        }
        if (owner && stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                setOrderedToSit(!isOrderedToSit());
                waitingPosition = isOrderedToSit() ? position() : null;
                getNavigation().stop();
                player.displayClientMessage(Component.translatable(isOrderedToSit()
                        ? "message.yagens_mobs.bubble_salamander.wait" : "message.yagens_mobs.bubble_salamander.follow"), true);
            } else if (isBaby()) {
                player.displayClientMessage(Component.translatable("message.yagens_mobs.bubble_salamander.baby"), true);
            } else if (eyeInWater(player) && isInWater() && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && !isWorn() && wornBy(player) == null) {
                attach(player);
                player.displayClientMessage(Component.translatable("message.yagens_mobs.bubble_salamander.worn",
                        Component.keybind("key.yagens_mobs.remove_bubble_salamander")), true);
            } else {
                player.displayClientMessage(Component.translatable("message.yagens_mobs.bubble_salamander.wear_hint"), true);
            }
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    private void feed(Player player, ItemStack stack) {
        stack.consume(1, player);
        playSound(SoundEvents.GENERIC_EAT, .65F, 1.2F);
        if (isTame()) {
            heal(4);
            if (isBaby()) {
                ageUp(getSpeedUpSecondsWhenFeeding(-getAge()), true);
            } else if (canFallInLove()) {
                setInLove(player);
            }
            return;
        }
        String key = player.getStringUUID();
        int progress = trust.getInt(key) + 1;
        if (progress == FISH_TO_TAME) {
            tame(player);
            trust = new CompoundTag();
            setPersistenceRequired();
            level().broadcastEntityEvent(this, (byte) 7);
        } else {
            trust.putInt(key, progress);
            setPersistenceRequired();
            level().broadcastEntityEvent(this, (byte) 6);
        }
    }

    private void attach(Player player) {
        if (isLeashed()) {
            dropLeash(true, true);
        }
        attachedPlayer = player;
        waitingPosition = null;
        entityData.set(WEARER, player.getId());
        wornTicks = 0;
        getNavigation().stop();
        setNoGravity(true);
        noPhysics = true;
        alignToWearer(player);
        player.setAirSupply(player.getMaxAirSupply());
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        if (!CreatureCue.BUBBLE_ATTACH.play(this, .7F)) {
            playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, .55F, 1.3F);
        }
    }

    public void detach() {
        Player player = attachedPlayer;
        if (player == null) {
            return;
        }
        CreatureCue.BUBBLE_RELEASE.play(this, .65F);
        attachedPlayer = null;
        entityData.set(WEARER, -1);
        wornTicks = 0;
        setNoGravity(false);
        noPhysics = false;
        setDeltaMovement(Vec3.ZERO);
        // The mob's current position remains the fallback if the player teleports or no adjacent water fits.
        if (player.level() == level() && distanceToSqr(player) < 16) {
            Vec3 eye = player.getEyePosition();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos pos = BlockPos.containing(eye.x, eye.y - .65, eye.z).relative(side);
                Vec3 at = Vec3.atBottomCenterOf(pos).add(0, .12, 0);
                if (level().getFluidState(pos).is(FluidTags.WATER)
                        && level().noCollision(this, getBoundingBox().move(at.subtract(position())))) {
                    setPos(at);
                    break;
                }
            }
        }
    }

    private void alignToWearer(Player player) {
        Vec3 eye = player.getEyePosition();
        setPos(eye.x, eye.y - WEAR_OFFSET, eye.z);
        setYRot(player.getYHeadRot());
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && attachedPlayer != null
                && (attachedPlayer.isRemoved() || !attachedPlayer.isAlive() || attachedPlayer.level() != level()
                || isBaby() || distanceToSqr(attachedPlayer) > 144 || !eyeInWater(attachedPlayer)
                || !attachedPlayer.getItemBySlot(EquipmentSlot.HEAD).isEmpty())) {
            detach();
        }
        Vec3 beforeTick = position();
        super.tick();
        if (!level().isClientSide) {
            entityData.set(SWIM_STROKE, !isWorn() && isInWater()
                    && position().distanceToSqr(beforeTick) > .00016);
            if (!isOrderedToSit() || isWorn() || isLeashed()) {
                waitingPosition = null;
            } else if (waitingPosition == null) {
                waitingPosition = position();
            }
        }
        if (isWorn()) {
            wornTicks++;
            Player player = wearer();
            if (player != null) {
                alignToWearer(player);
            }
        } else {
            wornTicks = 0;
        }
    }

    @Override
    public boolean isPickable() {
        return !isWorn() && super.isPickable();
    }

    @Nullable Vec3 waitingPosition() {
        return waitingPosition;
    }

    @Override
    public boolean isPushable() {
        return !isWorn() && super.isPushable();
    }

    @Override
    public boolean canBeLeashed() {
        return !isWorn() && super.canBeLeashed();
    }

    @Override
    public boolean isPushedByFluid() {
        return !isWorn() && !isOrderedToSit() && super.isPushedByFluid();
    }

    @Override
    public boolean isEffectiveAi() {
        return !isWorn() && super.isEffectiveAi();
    }

    @Override
    public void travel(Vec3 input) {
        if (isWorn()) {
            return;
        }
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.87));
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean fromBucket() {
        return fromBucket;
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.fromBucket = fromBucket;
        if (fromBucket) {
            setPersistenceRequired();
        }
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(ModItems.BUBBLE_SALAMANDER_BUCKET.get());
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_FISH;
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, this::saveCompanion);
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUUID(tag.getUUID("Owner"));
            setTame(true, false);
        }
        setOrderedToSit(tag.getBoolean("Waiting"));
        setAge(tag.getInt("Age"));
        readAppearance(tag);
        trust = tag.getCompound("Trust").copy();
    }

    private void saveCompanion(CompoundTag tag) {
        if (getOwnerUUID() != null) {
            tag.putUUID("Owner", getOwnerUUID());
        }
        tag.putBoolean("Waiting", isOrderedToSit());
        tag.putInt("Age", getAge());
        writeAppearance(tag);
        if (!trust.isEmpty()) {
            tag.put("Trust", trust.copy());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("FromBucket", fromBucket);
        writeAppearance(tag);
        if (waitingPosition != null) {
            CompoundTag anchor = new CompoundTag();
            anchor.putDouble("X", waitingPosition.x);
            anchor.putDouble("Y", waitingPosition.y);
            anchor.putDouble("Z", waitingPosition.z);
            anchor.putString("Dimension", level().dimension().location().toString());
            tag.put("WaitingPosition", anchor);
        }
        if (!trust.isEmpty()) {
            tag.put("Trust", trust.copy());
        }
        // Attachment belongs to the live player session. Persist a free-swimming entity on autosave.
        if (isWorn()) {
            tag.putBoolean("NoGravity", false);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInSittingPose(false);
        setFromBucket(tag.getBoolean("FromBucket"));
        readAppearance(tag);
        waitingPosition = null;
        CompoundTag anchor = tag.getCompound("WaitingPosition");
        if (anchor.getString("Dimension").equals(level().dimension().location().toString())) {
            double x = anchor.getDouble("X");
            double y = anchor.getDouble("Y");
            double z = anchor.getDouble("Z");
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                waitingPosition = new Vec3(x, y, z);
            }
        }
        trust = tag.getCompound("Trust").copy();
    }

    private void writeAppearance(CompoundTag tag) {
        tag.putBoolean("Female", isFemale());
        tag.putString("Color", color().id());
        if (color() == BubbleSalamanderColor.MIXED) {
            tag.putLong("MixedSeed", mixedSeed());
        }
    }

    private void readAppearance(CompoundTag tag) {
        setFemale(tag.contains("Female") ? tag.getBoolean("Female") : (getUUID().getLeastSignificantBits() & 1L) != 0L);
        long seed = tag.contains("MixedSeed") ? tag.getLong("MixedSeed")
                : Math.floorMod(tag.getByte("MixedPattern"), BubbleSalamanderColor.LEGACY_MIXED_PATTERNS);
        setColor(new BubbleSalamanderColor.Coat(BubbleSalamanderColor.byId(tag.getString("Color")), seed));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (isWorn()) {
                Player player = wearer();
                return state.setAndContinue(wornTicks <= 16 ? ATTACH
                        : player != null && player.isVisuallySwimming() ? WORN_SWIM : WORN);
            }
            if (isInWater()) {
                return state.setAndContinue(entityData.get(SWIM_STROKE) ? SWIM : WATER_IDLE);
            }
            return state.setAndContinue(state.isMoving() ? CRAWL : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private final class WaterSteering extends SmoothSwimmingMoveControl {
        WaterSteering() {
            super(BubbleSalamander.this, 60, 16, .74F, .32F, false);
        }

        @Override
        public void tick() {
            super.tick();
            if (!isInWater() || operation != Operation.MOVE_TO || getNavigation().isDone()) {
                if (isInWater()) {
                    setXRot(rotlerp(getXRot(), 0, 4));
                }
                return;
            }

            double dx = wantedX - getX();
            double dz = wantedZ - getZ();
            double horizontal = Math.hypot(dx, dz);
            if (horizontal > .20) {
                float heading = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90;
                float error = Math.abs(Mth.wrapDegrees(heading - getYRot()));
                float thrust = 1 - Mth.clamp((error - 12) / 55, 0, 1);
                setZza(BubbleSalamander.this.zza * thrust);
                if (error > 28) {
                    setDeltaMovement(getDeltaMovement().multiply(.60, .90, .60));
                }
            }
        }
    }

    private final class CruiseGoal extends Goal {
        @Nullable private Path route;
        private int nextAttempt;
        private int nextProgressCheck;
        private Vec3 lastPosition = Vec3.ZERO;

        CruiseGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean canCruise() {
            if (!isInWater() || isWorn() || isOrderedToSit() || isLeashed()) {
                return false;
            }
            LivingEntity owner = getOwner();
            // A cruise leg must not immediately cross the distance that starts following again.
            return owner == null || !owner.isInWater();
        }

        @Override
        public boolean canUse() {
            if (!canCruise() || !getNavigation().isDone() || tickCount < nextAttempt) {
                return false;
            }
            nextAttempt = tickCount + 36 + random.nextInt(45);
            for (int attempt = 0; attempt < 3; attempt++) {
                Vec3 destination = BehaviorUtils.getRandomSwimmablePos(BubbleSalamander.this, 8, 2);
                if (destination == null || destination.subtract(position()).horizontalDistanceSqr() < 9) {
                    continue;
                }
                Path candidate = getNavigation().createPath(BlockPos.containing(destination), 0);
                if (candidate != null && candidate.canReach() && staysInWater(candidate)) {
                    route = candidate;
                    return true;
                }
            }
            return false;
        }

        private boolean staysInWater(Path candidate) {
            for (int index = 0; index < candidate.getNodeCount(); index++) {
                if (!level().getFluidState(candidate.getNode(index).asBlockPos()).is(FluidTags.WATER)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void start() {
            if (route != null) {
                getNavigation().moveTo(route, 1.0);
            }
            lastPosition = position();
            nextProgressCheck = tickCount + 30;
        }

        @Override
        public boolean canContinueToUse() {
            return canCruise() && !getNavigation().isDone();
        }

        @Override
        public void tick() {
            if (tickCount < nextProgressCheck) {
                return;
            }
            // An unreachable corner can leave navigation active while the swimmer oscillates around one node.
            if (distanceToSqr(lastPosition) < .16) {
                getNavigation().stop();
            }
            lastPosition = position();
            nextProgressCheck = tickCount + 30;
        }

        @Override
        public void stop() {
            getNavigation().stop();
            route = null;
            nextAttempt = tickCount + 36 + random.nextInt(45);
        }
    }

    private final class SeekWaterGoal extends Goal {
        @Nullable private BlockPos target;

        SeekWaterGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isWorn() || isInWater() || !onGround() || isLeashed() || isOrderedToSit() || tickCount % 20 != 0) {
                return false;
            }
            target = null;
            double best = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-5, -2, -5),
                    blockPosition().offset(5, 1, 5))) {
                double distance = pos.distSqr(blockPosition());
                if (distance < best && level().getFluidState(pos).is(FluidTags.WATER)) {
                    target = pos.immutable();
                    best = distance;
                }
            }
            return target != null;
        }

        @Override
        public void start() {
            getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !isWorn() && !isInWater() && !isLeashed() && !getNavigation().isDone();
        }
    }

    private final class FollowSwimmerGoal extends Goal {
        FollowSwimmerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getOwner();
            return owner != null && !isWorn() && !isOrderedToSit() && !isLeashed() && owner.isInWater()
                    && distanceToSqr(owner) > 9;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = getOwner();
            return owner != null && !isWorn() && !isOrderedToSit() && !isLeashed() && owner.isInWater()
                    && distanceToSqr(owner) > 3;
        }

        @Override
        public void tick() {
            LivingEntity owner = getOwner();
            if (owner != null && tickCount % 10 == 0) {
                getNavigation().moveTo(owner, 1.16);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}

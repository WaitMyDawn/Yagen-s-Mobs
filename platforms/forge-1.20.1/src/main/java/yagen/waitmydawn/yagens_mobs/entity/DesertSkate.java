package yagen.waitmydawn.yagens_mobs.entity;

import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.compat.LegacyItemData;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;

public final class DesertSkate extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Byte> BURROW_PHASE =
            SynchedEntityData.defineId(DesertSkate.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_STARTED_AT =
            SynchedEntityData.defineId(DesertSkate.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> EATING_UNTIL =
            SynchedEntityData.defineId(DesertSkate.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> STING_STARTED_AT =
            SynchedEntityData.defineId(DesertSkate.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> JUVENILE =
            SynchedEntityData.defineId(DesertSkate.class, EntityDataSerializers.BOOLEAN);
    private static final java.util.UUID JUVENILE_ATTRIBUTES = java.util.UUID.fromString("42fc2487-bfe8-4f92-a208-f940aa10e789");
    private static final int GROWTH_TICKS = 20 * 60 * 20;
    private static final int FEED_GROWTH_TICKS = 2 * 60 * 20;
    private static final BurrowPhase[] BURROW_PHASES = BurrowPhase.values();
    private static final double BAIT_RANGE = 32.0;
    private static final double BAIT_REACH = 1.15;
    private static final double BAIT_EMERGE_RADIUS = 0.5;
    private static final int BAIT_LOCK_TICKS = 12 * 20;
    private static final int BAIT_RETRY_TICKS = 3 * 20;
    private static final int EATING_TICKS = 18;
    private static final int REGENERATION_TICKS = 30 * 20;
    private static final double AMBUSH_RANGE = 4.0;
    private static final double RETREAT_DISTANCE = 4.25;
    private static final double HUNT_RANGE = 16.0;
    private static final int SPIKE_COOLDOWN = 24;
    private static final int RETURN_IDLE_TICKS = 80;
    private static final int RETURN_ROLL_INTERVAL = 20;
    private static final float RETURN_CHANCE = 0.1F;

    private final SkateBurrow burrow = new SkateBurrow(this);
    private final SkateBreeding breeding = new SkateBreeding(this);
    private final SkateJuvenile juvenile = new SkateJuvenile(this, burrow);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private ItemEntity bait;
    @Nullable private ItemEntity baitAtExit;
    @Nullable private LivingEntity ambushPrey;
    private int nextBaitScan;
    private int baitLockUntil;
    private int nextBaitLock;
    private int nextAmbush;
    private int nextPathUpdate;
    private int nextShot;
    private int lostSightTicks;
    private int idleTicks;
    private int growthTicks;

    private enum SkatePack implements SpawnGroupData {
        INSTANCE
    }

    public enum BurrowPhase {
        SURFACE(0), DIVING(12), BURIED(0), EMERGING(12), AMBUSHING(12);

        private final int duration;

        BurrowPhase(int duration) {
            this.duration = duration;
        }
    }

    public DesertSkate(EntityType<? extends DesertSkate> type, Level level) {
        super(type, level);
        this.nextBaitScan = random.nextInt(24);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SkateAnimationController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.FOLLOW_RANGE, BAIT_RANGE);
    }

    public static boolean checkSpawnPlacement(EntityType<DesertSkate> type, ServerLevelAccessor level,
                                              MobSpawnType reason, BlockPos pos, RandomSource random) {
        return ServerConfigs.DESERT_SKATE.canSpawn()
                && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getBlockState(pos.below()).is(ModTags.BURROWABLE_SAND);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.DESERT_SKATE.maxGroupCount();
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        // Buried spawns can occur in daylight. The monster light penalty would reject them later in the pipeline.
        return level.getBlockState(pos.below()).is(ModTags.BURROWABLE_SAND) ? 1.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BURROW_PHASE, (byte) BurrowPhase.SURFACE.ordinal());
        entityData.define(PHASE_STARTED_AT, 0L);
        entityData.define(EATING_UNTIL, 0L);
        entityData.define(STING_STARTED_AT, -1L);
        entityData.define(JUVENILE, false);
    }

    @Override
    public boolean isBaby() {
        return entityData.get(JUVENILE);
    }

    @Override
    public void setBaby(boolean baby) {
        setGrowthTicks(baby ? GROWTH_TICKS : 0);
    }

    private void setGrowthTicks(int ticks) {
        growthTicks = Mth.clamp(ticks, 0, GROWTH_TICKS);
        boolean baby = growthTicks > 0;
        entityData.set(JUVENILE, baby);
        if (!level().isClientSide) {
            juvenileAttribute(Attributes.MAX_HEALTH, -10.0, baby);
            juvenileAttribute(Attributes.ARMOR, -4.0, baby);
            juvenileAttribute(Attributes.ARMOR_TOUGHNESS, -2.0, baby);
            if (baby) {
                entityData.set(STING_STARTED_AT, -1L);
                if (getTarget() != null) {
                    setTarget(null);
                }
            }
            setHealth(Math.min(getHealth(), getMaxHealth()));
        }
    }

    private void juvenileAttribute(Attribute attribute, double difference, boolean baby) {
        var instance = getAttribute(attribute);
        if (instance.getModifier(JUVENILE_ATTRIBUTES) != null) {
            instance.removeModifier(JUVENILE_ATTRIBUTES);
        }
        if (baby) {
            instance.addTransientModifier(new AttributeModifier(JUVENILE_ATTRIBUTES, "Juvenile", difference,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isBaby() && super.canAttack(target);
    }

    public float getAgeScale() {
        return isBaby() ? 0.5F : 1.0F;
    }

    @Override
    public float getScale() {
        return getAgeScale();
    }

    @Override
    protected boolean shouldDropLoot() {
        // Juvenile drops are selected by the entity loot table.
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack food = player.getItemInHand(hand);
        if (burrowPhase() != BurrowPhase.SURFACE || food.isEmpty() || !food.is(ModTags.SKATE_BAIT)) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide) {
            ItemStack bite = LegacyItemData.take(food, 1, player);
            setPersistenceRequired();
            eat(bite, player.position().subtract(position()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public BurrowPhase burrowPhase() {
        return BURROW_PHASES[entityData.get(BURROW_PHASE)];
    }

    public boolean isBurrowed() {
        return burrowPhase() == BurrowPhase.BURIED || burrowPhase() == BurrowPhase.AMBUSHING;
    }

    SkateBreeding breeding() {
        return breeding;
    }

    @Nullable
    Vec3 burrowSurface() {
        return burrow.surface();
    }

    void setBurrowPhase(BurrowPhase phase) {
        if (phase != BurrowPhase.AMBUSHING) {
            ambushPrey = null;
        }
        if (phase == BurrowPhase.BURIED || phase == BurrowPhase.DIVING) {
            baitAtExit = null;
        }
        if (phase == BurrowPhase.SURFACE) {
            baitLockUntil = 0;
        }
        entityData.set(PHASE_STARTED_AT, level().getGameTime());
        entityData.set(BURROW_PHASE, (byte) phase.ordinal());
        if (phase != BurrowPhase.SURFACE) {
            entityData.set(STING_STARTED_AT, -1L);
            breeding.leavePair();
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        idleTicks = 0;
    }

    public float burrowProgress(float partialTick) {
        int duration = burrowPhase().duration;
        if (duration == 0) {
            return 0.0F;
        }
        return Mth.clamp(burrowElapsed(partialTick) / duration, 0.0F, 1.0F);
    }

    float burrowElapsed(float partialTick) {
        return Math.max(0.0F, level().getGameTime() - entityData.get(PHASE_STARTED_AT) + partialTick);
    }

    float eatingElapsed(float partialTick) {
        long remaining = entityData.get(EATING_UNTIL) - level().getGameTime();
        return remaining > 0 && remaining <= EATING_TICKS
                ? EATING_TICKS - remaining + partialTick : -1.0F;
    }

    boolean isEating() {
        return level().getGameTime() < entityData.get(EATING_UNTIL);
    }

    public float stingElapsed(float partialTick) {
        long started = entityData.get(STING_STARTED_AT);
        return started >= 0 ? level().getGameTime() - started + partialTick : -1.0F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BURROW_PHASE.equals(key)) {
            noPhysics = burrowPhase() != BurrowPhase.SURFACE;
            setNoGravity(noPhysics);
        } else if (JUVENILE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // The current position is revalidated on reload, including saves made mid-transition.
        tag.putBoolean("Burrowed", burrowPhase() != BurrowPhase.SURFACE);
        if (growthTicks > 0) {
            tag.putInt("GrowthTicks", growthTicks);
        }
        breeding.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBurrowPhase(tag.getBoolean("Burrowed") ? BurrowPhase.BURIED : BurrowPhase.SURFACE);
        setGrowthTicks(tag.getInt("GrowthTicks"));
        breeding.load(tag);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group, @Nullable CompoundTag dataTag) {
        var data = super.finalizeSpawn(level, difficulty, reason, group, dataTag);
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            if (data == SkatePack.INSTANCE && random.nextDouble() < ServerConfigs.DESERT_SKATE_JUVENILE_CHANCE.get()) {
                setBaby(true);
            }
            if (data == null) {
                data = SkatePack.INSTANCE;
            }
            burrow.enter(false);
        }
        return data;
    }

    @Override
    public void travel(Vec3 input) {
        if (burrowPhase() == BurrowPhase.SURFACE) {
            super.travel(input);
        }
    }

    @Override
    public boolean isPushable() {
        return burrowPhase() == BurrowPhase.SURFACE && super.isPushable();
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide && isAlive()) {
            if (growthTicks > 0 && --growthTicks == 0) {
                setGrowthTicks(0);
            }
            burrow.tick();
        }
        super.aiStep();
        if (level().isClientSide && tickCount % 4 == 0 && ClientConfigs.SAND_TRAILS.get()) {
            burrow.showTrail();
        }
    }

    @Override
    protected void customServerAiStep() {
        if (isBaby() && juvenile.tick()) {
            return;
        }
        if (burrowPhase() != BurrowPhase.SURFACE) {
            if (isBurrowed()) {
                tickBurrowed();
            }
            return;
        }
        if (isEating()) {
            return;
        }
        if (baitAtExit != null) {
            ItemEntity food = baitAtExit;
            baitAtExit = null;
            bait = isBait(food) ? food : null;
            if (eatBait()) {
                return;
            }
        }
        if (!isBaby() && breeding.tick()) {
            if (getTarget() != null) {
                setTarget(null);
                lostSightTicks = 0;
            }
            cancelStingWindup();
            idleTicks = 0;
            return;
        }

        LivingEntity target = getTarget();
        if (target != null && (!isPrey(target) || distanceToSqr(target) > HUNT_RANGE * HUNT_RANGE
                || lostSightTicks >= 100)) {
            setTarget(null);
            getNavigation().stop();
            lostSightTicks = 0;
            target = null;
        }
        if (!isBaby() && target == null && (tickCount + getId()) % 10 == 0) {
            LivingEntity nearby = nearestPrey(position(), HUNT_RANGE,
                    getBoundingBox().inflate(HUNT_RANGE, 6.0, HUNT_RANGE), getSensing()::hasLineOfSight);
            if (nearby != null) {
                setTarget(nearby);
                target = getTarget();
            }
        }
        if (target != null) {
            idleTicks = 0;
            hunt(target);
            return;
        }
        cancelStingWindup();

        findBait();
        if (bait != null) {
            idleTicks = 0;
            if (!eatBait() && tickCount >= nextPathUpdate) {
                getNavigation().moveTo(bait, 1.1);
                nextPathUpdate = tickCount + 12;
            }
            return;
        }
        if (level().isRaining()) {
            idleTicks = 0;
        } else if (!isBaby() && !breeding.isReady() && ++idleTicks >= RETURN_IDLE_TICKS
                && (idleTicks - RETURN_IDLE_TICKS) % RETURN_ROLL_INTERVAL == 0
                && onGround() && random.nextFloat() < RETURN_CHANCE && burrow.enter(true)) {
            return;
        }
        if (getNavigation().isDone() && random.nextInt(50) == 0) {
            Vec3 wander = DefaultRandomPos.getPos(this, 8, 3);
            if (wander != null) {
                getNavigation().moveTo(wander.x, wander.y, wander.z, 0.85);
            }
        }
    }

    private void tickBurrowed() {
        if (burrowPhase() == BurrowPhase.AMBUSHING) {
            finishAmbush();
            return;
        }
        if (level().isRaining()) {
            burrow.swim(null);
            return;
        }
        if (pursueBuriedBait()) {
            return;
        }
        if (tickCount >= nextAmbush && (tickCount + getId()) % 4 == 0) {
            Vec3 surface = burrow.surface();
            if (surface != null) {
                AABB above = new AABB(surface.x - AMBUSH_RANGE, surface.y - 0.25, surface.z - AMBUSH_RANGE,
                        surface.x + AMBUSH_RANGE, surface.y + AMBUSH_RANGE, surface.z + AMBUSH_RANGE);
                LivingEntity prey = nearestPrey(surface, AMBUSH_RANGE, above, candidate -> canAmbush(surface, candidate));
                if (prey != null) {
                    ambushPrey = prey;
                    setBurrowPhase(BurrowPhase.AMBUSHING);
                    burrow.warnAmbush(surface, 0.0F);
                    return;
                }
            }
        }
        burrow.swim(null);
    }

    private boolean canAmbush(Vec3 surface, LivingEntity prey) {
        return isPrey(prey) && prey.distanceToSqr(surface) <= AMBUSH_RANGE * AMBUSH_RANGE
                && prey.getY() >= surface.y - 0.25 && clearLine(surface.add(0.0, 0.2, 0.0), prey.getEyePosition());
    }

    private void finishAmbush() {
        Vec3 surface = burrow.surface();
        if (surface == null || ambushPrey == null || !canAmbush(surface, ambushPrey)) {
            nextAmbush = tickCount + 12;
            setBurrowPhase(BurrowPhase.BURIED);
            return;
        }
        float progress = burrowProgress(0.0F);
        if (progress >= 1.0F) {
            setTarget(ambushPrey);
            if (getTarget() == null) {
                nextAmbush = tickCount + 12;
                setBurrowPhase(BurrowPhase.BURIED);
                return;
            }
            burrow.emerge(surface);
            nextShot = tickCount + BurrowPhase.EMERGING.duration + 6;
            nextPathUpdate = tickCount;
            lostSightTicks = 0;
        } else if ((int) (progress * BurrowPhase.AMBUSHING.duration) % 3 == 0) {
            burrow.warnAmbush(surface, progress);
        }
    }

    private boolean pursueBuriedBait() {
        if (baitLockUntil != 0 && (tickCount >= baitLockUntil || bait == null || !isBuriedBait(bait))) {
            bait = null;
            baitLockUntil = 0;
            nextBaitLock = tickCount + BAIT_RETRY_TICKS;
        }
        if (tickCount < nextBaitLock) {
            return false;
        }
        if (baitLockUntil == 0) {
            findBait(this::isBuriedBait);
            if (bait == null) {
                return false;
            }
            baitLockUntil = tickCount + BAIT_LOCK_TICKS;
        }
        Vec3 foodPosition = bait.position();
        Vec3 surface = burrow.surface();
        if (surface != null && foodPosition.y >= surface.y - 0.05
                && foodPosition.subtract(surface).horizontalDistanceSqr() <= BAIT_EMERGE_RADIUS * BAIT_EMERGE_RADIUS
                && canReachBait(bait, surface)) {
            baitAtExit = bait;
            baitLockUntil = 0;
            burrow.emerge(surface);
        } else {
            burrow.swim(foodPosition.add(0.0, -0.75, 0.0));
        }
        return true;
    }

    @Nullable
    private LivingEntity nearestPrey(Vec3 origin, double radius, AABB search, Predicate<LivingEntity> visible) {
        return level().getEntitiesOfClass(LivingEntity.class, search,
                        prey -> isPrey(prey) && prey.distanceToSqr(origin) <= radius * radius && visible.test(prey))
                .stream().min(Comparator.comparingDouble(prey -> prey.distanceToSqr(origin))).orElse(null);
    }

    private boolean isPrey(LivingEntity candidate) {
        return candidate.isAlive() && candidate.getType().is(ModTags.SKATE_PREY) && canAttack(candidate);
    }

    private boolean clearLine(Vec3 start, Vec3 end) {
        return level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void findBait() {
        findBait(DesertSkate::isBait);
    }

    private void findBait(Predicate<ItemEntity> suitable) {
        if (bait != null && (!suitable.test(bait) || distanceToSqr(bait) > BAIT_RANGE * BAIT_RANGE)) {
            bait = null;
        }
        if (tickCount < nextBaitScan) {
            return;
        }
        nextBaitScan = tickCount + 24 + random.nextInt(16);
        bait = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(BAIT_RANGE),
                        item -> suitable.test(item) && distanceToSqr(item) <= BAIT_RANGE * BAIT_RANGE)
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private static boolean isBait(ItemEntity item) {
        return item.isAlive() && item.getItem().is(ModTags.SKATE_BAIT);
    }

    private boolean isBuriedBait(ItemEntity item) {
        if (!isBait(item) || distanceToSqr(item) > BAIT_RANGE * BAIT_RANGE) {
            return false;
        }
        BlockPos support = BlockPos.containing(item.getX(), item.getY() + 0.05, item.getZ()).below();
        return level().hasChunkAt(support) && level().getBlockState(support).is(ModTags.BURROWABLE_SAND);
    }

    private boolean canReachBait(ItemEntity food, Vec3 origin) {
        return food.distanceToSqr(origin) <= BAIT_REACH * BAIT_REACH
                && clearLine(origin.add(0.0, 0.1, 0.0), food.getBoundingBox().getCenter());
    }

    private boolean eatBait() {
        if (bait == null || !canReachBait(bait, position())) {
            return false;
        }
        Vec3 towardFood = bait.position().subtract(position());
        var remainder = bait.getItem().copy();
        ItemStack bite = remainder.split(1);
        if (remainder.isEmpty()) {
            bait.discard();
        } else {
            bait.setItem(remainder);
        }
        bait = null;
        nextBaitScan = tickCount + 32;
        eat(bite, towardFood);
        return true;
    }

    private void eat(ItemStack bite, Vec3 towardFood) {
        if (isBaby()) {
            setGrowthTicks(growthTicks - FEED_GROWTH_TICKS);
        } else {
            breeding.afterMeal();
        }
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_TICKS, 1));
        idleTicks = 0;
        cancelStingWindup();
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        entityData.set(EATING_UNTIL, level().getGameTime() + EATING_TICKS);
        if (towardFood.horizontalDistanceSqr() > 1.0E-6) {
            float yaw = (float) (Mth.atan2(towardFood.z, towardFood.x) * Mth.RAD_TO_DEG - 90.0);
            setYRot(yaw);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
        }
        float ageScale = getAgeScale();
        Vec3 mouth = position().add(getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize().scale(0.6 * ageScale))
                .add(0.0, 0.22 * ageScale, 0.0);
        ((ServerLevel) level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bite),
                mouth.x, mouth.y, mouth.z, 6, 0.12, 0.06, 0.12, 0.04);
        playSound(SoundEvents.FOX_EAT, 0.65F, 0.85F);
    }

    private void hunt(LivingEntity target) {
        boolean visible = getSensing().hasLineOfSight(target);
        lostSightTicks = visible ? 0 : lostSightTicks + 1;
        getLookControl().setLookAt(target, 35.0F, 30.0F);
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 toTarget = targetCenter.subtract(position());
        float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG - 90.0);
        Vec3 muzzle = SkateSting.muzzle(this, yaw);
        Vec3 aim = targetCenter.subtract(muzzle);
        boolean tooClose = toTarget.horizontalDistanceSqr() < RETREAT_DISTANCE * RETREAT_DISTANCE;
        boolean inRange = aim.lengthSqr() <= SkateSpike.RANGE * SkateSpike.RANGE;
        if (tooClose || !inRange || !visible || !clearLine(muzzle, targetCenter)) {
            cancelStingWindup();
            if (tickCount >= nextPathUpdate) {
                nextPathUpdate = tickCount + 8;
                if (tooClose) {
                    Vec3 retreat = DefaultRandomPos.getPosAway(this, 8, 3, target.position());
                    if (retreat != null) {
                        getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.25);
                    } else {
                        getNavigation().stop();
                    }
                } else {
                    getNavigation().moveTo(target, 1.1);
                }
            }
            return;
        }
        getNavigation().stop();
        float elapsed = stingElapsed(0.0F);
        if (elapsed >= 0.0F && elapsed < SkateSting.TOTAL_TICKS) {
            setYBodyRot(yaw);
            if (elapsed >= SkateSting.WINDUP_TICKS && tickCount >= nextShot) {
                SkateSpike.fire((ServerLevel) level(), this, muzzle, aim);
                nextShot = tickCount + SPIKE_COOLDOWN;
                playSound(SoundEvents.LLAMA_SPIT, 0.8F, 1.25F);
            }
        } else if (tickCount + SkateSting.WINDUP_TICKS >= nextShot) {
            entityData.set(STING_STARTED_AT, level().getGameTime());
            setYBodyRot(yaw);
        }
    }

    private void cancelStingWindup() {
        float elapsed = stingElapsed(0.0F);
        if (elapsed >= 0.0F && elapsed <= SkateSting.WINDUP_TICKS) {
            entityData.set(STING_STARTED_AT, -1L);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, strikesBack(source) ? amount * 0.1F : amount);
    }

    @Nullable
    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        if (!level().isClientSide && burrowPhase() != BurrowPhase.SURFACE && !stack.isEmpty()) {
            Vec3 surface = burrow.surface();
            if (surface != null) {
                offsetY += (float) (surface.y - getY() + 0.05);
            }
        }
        return super.spawnAtLocation(stack, offsetY);
    }

    private boolean strikesBack(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return false;
        }
        Vec3 incoming;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            incoming = projectile.getDeltaMovement().reverse();
        } else if (source.getDirectEntity() instanceof LivingEntity attacker) {
            incoming = attacker.getEyePosition().subtract(getBoundingBox().getCenter());
        } else {
            Vec3 damagePosition = source.getSourcePosition();
            if (damagePosition == null) {
                return false;
            }
            incoming = damagePosition.subtract(getBoundingBox().getCenter());
        }
        // Only the dorsal plate has the extra reduction. Projectile positions can already overlap the hitbox.
        return incoming.normalize().y >= 0.6;
    }
}

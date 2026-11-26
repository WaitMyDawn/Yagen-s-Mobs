package yagen.waitmydawn.yagens_mobs.entity.rockhanger;

import net.minecraft.sounds.SoundEvent;
import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;

public final class RockHanger extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(RockHanger.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3fc> CLIMB_FORWARD =
            SynchedEntityData.defineId(RockHanger.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SUPPORT =
            SynchedEntityData.defineId(RockHanger.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> ANCHOR =
            SynchedEntityData.defineId(RockHanger.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> TUFT_CLIPPED =
            SynchedEntityData.defineId(RockHanger.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceKey<DamageType> AMBUSH_DAMAGE = damageKey("rock_hanger_ambush");
    private static final ResourceKey<DamageType> MELEE_DAMAGE = damageKey("rock_hanger_melee");
    private static final RawAnimation CLING = RawAnimation.begin().thenLoop("animation.rock_hanger.cling");
    private static final RawAnimation LOWER = RawAnimation.begin().thenPlayAndHold("animation.rock_hanger.lower");
    private static final RawAnimation HARVEST = RawAnimation.begin().thenPlayAndHold("animation.rock_hanger.harvest");
    private static final RawAnimation DRAW_UP = RawAnimation.begin().thenPlayAndHold("animation.rock_hanger.draw_up");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.rock_hanger.crawl");
    private static final int WARNING_TICKS = 16;
    private static final int FEED_TICKS = 60;
    private static final int DEFENSE_TICKS = 200;
    private static final int MAX_REST_LENGTH = 6;
    static final double FORAGING_RADIUS = 1.5;
    private static final int GROUND_TARGET_RANGE = 12;

    public enum Phase {
        GROUND, IDLE, WARNING, STRIKE, SEEKING_BAIT, FEEDING, RETRACTING, LOWERING, FALLING, CLIMBING, BITING,
        DEFENSE_REEL, BRACED, SLIPPING
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Quaternionf previousPose = new Quaternionf();
    private final Quaternionf currentPose = new Quaternionf();
    private RockHangerSurfaces.Route route;
    private UUID bait;
    private UUID quarry;

    private int phaseTicks;
    private int nextSearch;
    private int restLength = MAX_REST_LENGTH;
    private int nextLengthChange;
    private boolean slippedThisReel;
    private double slipY;
    private int routeIndex;
    private int cornerTicks;
    private int biteDelay;
    private int unseenTicks;
    private int nextWander;
    private int retargetAfter;
    private int stalledChaseTicks;
    private Vec3 chaseSample;

    public RockHanger(EntityType<? extends RockHanger> type, Level level) {
        super(type, level);
        nextSearch = random.nextInt(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, GROUND_TARGET_RANGE)
                .add(Attributes.ATTACK_DAMAGE, 6);
    }

    public static boolean checkSpawnPlacement(EntityType<RockHanger> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return ServerConfigs.ROCK_HANGER.canSpawn()
                && level.getDifficulty() != Difficulty.PEACEFUL
                && level.isEmptyBlock(pos) && !level.canSeeSky(pos)
                && (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        || RockHangerSurfaces.nearCeiling(level, pos))
                && Monster.isDarkEnoughToSpawn(level, pos, random)
                && RockHangerSurfaces.findSpawnAnchor(level, pos, type) != null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CreatureCue.HANGER_HURT.orElse(super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CreatureCue.HANGER_DEATH.orElse(super.getDeathSound());
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.ROCK_HANGER.maxGroupCount();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                        @Nullable SpawnGroupData group) {
        var data = super.finalizeSpawn(level, difficulty, reason, group);
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
            BlockPos roof = RockHangerSurfaces.findSpawnAnchor(level, blockPosition(), getType());
            if (roof != null) {
                spawnSuspended(roof);
            }
        }
        return data;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, Phase.GROUND.ordinal()).define(SUPPORT, Direction.DOWN.get3DDataValue())
                .define(ANCHOR, Optional.empty()).define(TUFT_CLIPPED, false)
                .define(CLIMB_FORWARD, new Vector3f());
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    public Direction support() {
        return Direction.from3DDataValue(entityData.get(SUPPORT));
    }

    public Optional<BlockPos> anchor() {
        return entityData.get(ANCHOR);
    }

    public boolean isTuftClipped() {
        return entityData.get(TUFT_CLIPPED);
    }

    public Vec3 silkTop() {
        return anchor().map(pos -> new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)).orElse(position());
    }

    public Vec3 silkBottom() {
        return silkBottom(position());
    }

    private Vec3 silkBottom(Vec3 feet) {
        return feet.add(0, getBbHeight() / 2 + 0.18, 0);
    }

    public Quaternionf renderPose(float partialTick) {
        return new Quaternionf(previousPose).slerp(currentPose, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            updateRenderPose();
        }
    }

    private void updateRenderPose() {
        Direction side = support();
        Quaternionf pose = new Quaternionf();
        var forward = entityData.get(CLIMB_FORWARD);
        if (phase() == Phase.CLIMBING && side != Direction.DOWN && forward.lengthSquared() > 0.5F) {
            var up = Vec3.atLowerCornerOf(side.getUnitVec3i()).toVector3f();
            var right = new Vector3f(forward).cross(up);
            // Support and heading may arrive in separate updates. Rebuild the tangent basis on the active face.
            if (right.lengthSquared() > 1.e-5F) {
                right.normalize();
                var back = new Vector3f(right).cross(up);
                pose.setFromNormalized(new Matrix3f().setColumn(0, right).setColumn(1, up).setColumn(2, back));
            } else {
                pose.set(currentPose);
            }
        } else if (side.getAxis().isHorizontal()) {
            pose.rotationY(-side.toYRot() * Mth.DEG_TO_RAD).rotateX(Mth.HALF_PI);
        } else {
            pose.rotationY(Mth.PI - yBodyRot * Mth.DEG_TO_RAD);
            if (side == Direction.DOWN) {
                pose.rotateZ(Mth.PI);
            }
        }
        if (tickCount <= 1) {
            previousPose.set(pose);
            currentPose.set(pose);
        } else {
            previousPose.set(currentPose);
            currentPose.slerp(pose, 0.34F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("posture", 4, state -> state.setAndContinue(switch (phase()) {
            case WARNING, STRIKE, SEEKING_BAIT, LOWERING, BITING, SLIPPING -> LOWER;
            case FEEDING -> HARVEST;
            case RETRACTING, DEFENSE_REEL -> DRAW_UP;
            case CLIMBING -> CRAWL;
            case GROUND -> state.isMoving() ? CRAWL : CLING;
            default -> CLING;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void customServerAiStep(ServerLevel aiLevel) {
        super.customServerAiStep(aiLevel);
        ServerLevel level = (ServerLevel) level();
        phaseTicks++;
        if (anchor().isPresent()) {
            if (!RockHangerSurfaces.sturdy(level, anchor().orElseThrow(), Direction.UP)) {
                fall(null);
                return;
            }
            setDeltaMovement(Vec3.ZERO);
        }
        switch (phase()) {
            case IDLE -> watch(level);
            case WARNING -> windUp(level);
            case STRIKE -> strike(level);
            case SEEKING_BAIT -> seekBait(level);
            case FEEDING -> {
                if (phaseTicks >= FEED_TICKS) {
                    changePhase(Phase.RETRACTING);
                }
            }
            case RETRACTING, LOWERING -> reel(false);
            case DEFENSE_REEL -> reel(true);
            case BRACED -> {
                if (phaseTicks >= DEFENSE_TICKS) {
                    changePhase(Phase.LOWERING);
                }
            }
            case SLIPPING -> slip();
            case FALLING -> {
                if (onGround() || isInWater()) {
                    setSupport(Direction.DOWN);
                    changePhase(Phase.GROUND);
                }
            }
            case GROUND, BITING -> ground(level);
            case CLIMBING -> climb();
        }
    }

    private void watch(ServerLevel level) {
        Vec3 idle = idleFeet();
        if (position().distanceToSqr(idle) > 0.0025) {
            changePhase(Phase.RETRACTING);
            return;
        }
        if (tickCount < nextSearch) {
            return;
        }
        nextSearch = tickCount + 10;
        ItemEntity food = findBait(level);
        if (food != null) {
            bait = food.getUUID();
            changePhase(Phase.SEEKING_BAIT);
            return;
        }
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, foragingVolume(level), this::validQuarry)
                .stream().filter(prey -> reachable(prey.position()))
                .max(Comparator.comparingDouble(LivingEntity::getY)).orElse(null);
        if (target != null) {
            quarry = target.getUUID();
            changePhase(Phase.WARNING);
            CreatureCue.HANGER_WARN.playReplacing(this, 0.8F, SoundEvents.POINTED_DRIPSTONE_LAND, 0.65F);
        } else if (tickCount >= nextLengthChange) {
            restLength = 1 + (restLength + random.nextInt(MAX_REST_LENGTH - 1)) % MAX_REST_LENGTH;
            changePhase(idleFeet().y < getY() ? Phase.LOWERING : Phase.RETRACTING);
        }
    }

    private AABB foragingVolume(ServerLevel level) {
        Vec3 center = silkTop();
        return new AABB(center.x - FORAGING_RADIUS, level.getMinY(), center.z - FORAGING_RADIUS,
                center.x + FORAGING_RADIUS, getY() + getBbHeight(), center.z + FORAGING_RADIUS);
    }

    private boolean validQuarry(LivingEntity prey) {
        return prey.isAlive() && (prey instanceof Player player ? canHunt(player) : prey.getType().is(ModTags.ROCK_HANGER_PREY))
                && inForagingArea(prey.position()) && prey.getY() <= getY() + 0.8;
    }

    private void windUp(ServerLevel level) {
        if (!(level.getEntity(quarry) instanceof LivingEntity target) || !validQuarry(target) || !reachable(target.position())) {
            changePhase(Phase.RETRACTING);
            return;
        }
        if (phaseTicks % 4 == 1) {
            level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.15, target.getZ(),
                    4, 0.23, 0.05, 0.23, 0.01);
        }
        if (phaseTicks >= WARNING_TICKS) {
            changePhase(Phase.STRIKE);
        }
    }

    private void strike(ServerLevel level) {
        if (!(level.getEntity(quarry) instanceof LivingEntity target) || !validQuarry(target)
                || !moveToPrey(target.position(), 1.25)) {
            changePhase(Phase.RETRACTING);
            return;
        }
        if (getBoundingBox().inflate(0.16).intersects(target.getBoundingBox())) {
            hit(target, AMBUSH_DAMAGE, 12, 18, 24);
            if (target.getType().is(ModTags.ROCK_HANGER_PREY) && target.isDeadOrDying()) {
                beginFeeding();
            } else {
                changePhase(Phase.RETRACTING);
            }
        }
    }

    @Nullable
    private ItemEntity findBait(ServerLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, foragingVolume(level), this::validBait).stream()
                .filter(food -> reachable(food.position()))
                .max(Comparator.comparingDouble(ItemEntity::getY)).orElse(null);
    }

    private boolean validBait(ItemEntity item) {
        return item.isAlive() && item.getItem().is(ModTags.ROCK_HANGER_BAIT) && inForagingArea(item.position())
                && item.getY() <= getY() + 0.8;
    }

    private void seekBait(ServerLevel level) {
        if (!(level.getEntity(bait) instanceof ItemEntity item) || !validBait(item)
                || !moveToPrey(item.position(), 0.5)) {
            changePhase(Phase.RETRACTING);
            return;
        }
        if (getBoundingBox().inflate(0.35).intersects(item.getBoundingBox())) {
            ItemStack stack = item.getItem();
            if (stack.getCount() == 1) {
                item.discard();
            } else {
                item.setItem(stack.copyWithCount(stack.getCount() - 1));
            }
            heal(getMaxHealth() - getHealth());
            beginFeeding();
        }
    }

    private void beginFeeding() {
        entityData.set(TUFT_CLIPPED, false);
        changePhase(Phase.FEEDING);
        CreatureCue.HANGER_FEED.playReplacing(this, 0.85F, SoundEvents.GENERIC_EAT.value(), 0.58F);
    }

    private boolean inForagingArea(Vec3 position) {
        BlockPos roof = anchor().orElseThrow();
        return Math.abs(Mth.floor(position.x) - roof.getX()) <= 1
                && Math.abs(Mth.floor(position.z) - roof.getZ()) <= 1;
    }

    private Vec3 idleFeet() {
        return RockHangerSurfaces.idleFeet(anchor().orElseThrow(), getType(), restLength);
    }

    private boolean reachable(Vec3 feet) {
        return RockHangerSurfaces.sweepClear(level(), getType(), position(), feet)
                && RockHangerSurfaces.tetherClear(level(), silkTop(), silkBottom(feet));
    }

    private boolean moveToPrey(Vec3 goal, double speed) {
        Vec3 next = nextPosition(goal, speed);
        if (!reachable(next)) {
            return false;
        }
        moveToward(next, speed);
        return true;
    }

    private void reel(boolean defense) {
        Vec3 goal = defense ? RockHangerSurfaces.ceilingFeet(anchor().orElseThrow(), getType()) : idleFeet();
        double speed = defense ? 1.05 : 0.48;
        if (!defense && !slippedThisReel && goal.y > getY() + 0.55 && random.nextInt(120) == 0) {
            Vec3 dropped = position().add(0, -(1 + random.nextInt(2)), 0);
            if (reachable(dropped)) {
                slipY = dropped.y;
                slippedThisReel = true;
                changePhase(Phase.SLIPPING);
                return;
            }
        }
        Vec3 next = nextPosition(goal, speed);
        Vec3 center = new Vec3(goal.x, getY(), goal.z);
        if (!reachable(next)) {
            if (!center.closerThan(position(), 0.01) && moveToPrey(center, speed)) {
                return;
            }
            fall(null);
        } else if (moveToward(goal, speed)) {
            if (defense) {
                changePhase(Phase.BRACED);
            } else {
                startRest();
            }
        }
    }

    private void slip() {
        Vec3 dropped = new Vec3(getX(), slipY, getZ());
        if (!reachable(dropped) || moveToward(dropped, 0.38)) {
            changePhase(Phase.RETRACTING);
        }
    }

    private void startRest() {
        changePhase(Phase.IDLE);
        nextSearch = tickCount + 15;
        nextLengthChange = tickCount + 100 + random.nextInt(141);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) || shieldingTether()
                && (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile);
    }

    public boolean shieldingTether() {
        return phase() == Phase.DEFENSE_REEL || phase() == Phase.BRACED;
    }

    @Override
    public boolean hurtServer(ServerLevel hitLevel, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(hitLevel, source, amount);
        if (hurt && !isDeadOrDying() && anchor().isPresent() && !shieldingTether()) {
            bait = null;
            quarry = null;
            changePhase(Phase.DEFENSE_REEL);
        }
        return hurt;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (anchor().isEmpty() && phase() != Phase.CLIMBING) {
            super.knockback(strength, x, z);
        }
    }

    private void ground(ServerLevel level) {
        Player player = getTarget() instanceof Player target ? target : null;
        if (player != null) {
            unseenTicks = hasLineOfSight(player) ? 0 : unseenTicks + 1;
        }
        if (player != null && (!canHunt(player) || distanceToSqr(player) > Mth.square(GROUND_TARGET_RANGE)
                || unseenTicks > 45)) {
            setTarget(null);
            player = null;
            unseenTicks = 0;
            resetChase();
            getNavigation().stop();
        }
        if (player == null && tickCount >= nextSearch && route == null) {
            player = groundTarget(level, true);
            if (player == null && (onGround() || isInWater())) {
                seekCeiling();
                if (route == null && tickCount >= retargetAfter) {
                    player = groundTarget(level, false);
                }
            }
            if (player != null) {
                setTarget(player);
                nextSearch = tickCount;
            }
        }
        if (player != null) {
            route = null;
            if (phase() == Phase.BITING && phaseTicks >= 14) {
                changePhase(Phase.GROUND);
            }
            boolean inReach = isWithinMeleeAttackRange(player) && hasLineOfSight(player);
            if (inReach) {
                resetChase();
                getNavigation().stop();
            } else if (tickCount >= nextSearch && !pursue(player)) {
                return;
            }
            getLookControl().setLookAt(player, 30, 30);
            if (inReach && tickCount >= biteDelay) {
                biteDelay = tickCount + 24;
                changePhase(Phase.BITING);
                hit(player, MELEE_DAMAGE, 4, 6, 8);
            }
            return;
        }
        if (phase() == Phase.BITING) {
            changePhase(Phase.GROUND);
        }
        if (route == null && onGround() && getNavigation().isDone() && tickCount >= nextWander) {
            nextWander = tickCount + 55 + random.nextInt(36);
            Vec3 destination = LandRandomPos.getPos(this, 10, 4);
            if (destination != null) {
                getNavigation().moveTo(destination.x, destination.y, destination.z, 0.85);
            }
        }
        if (route != null) {
            var mount = route.steps().getFirst();
            if (RockHangerSurfaces.approachable(this, mount)) {
                getNavigation().stop();
                Vec3 contact = new Vec3(mount.feet().x, getY(), mount.feet().z);
                faceHorizontal(contact.subtract(position()), 22);
                if (moveToward(contact, 0.19)) {
                    routeIndex = 0;
                    entityData.set(CLIMB_FORWARD, new Vector3f());
                    changePhase(Phase.CLIMBING);
                }
            } else if (getNavigation().isDone() || tickCount >= nextSearch + 120) {
                route = null;
            }
        }
    }

    @Nullable
    private Player groundTarget(ServerLevel level, boolean meleeOnly) {
        return level.players().stream().filter(candidate -> canHunt(candidate)
                        && distanceToSqr(candidate) <= Mth.square(GROUND_TARGET_RANGE)
                        && (!meleeOnly || isWithinMeleeAttackRange(candidate)) && hasLineOfSight(candidate))
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private void seekCeiling() {
        route = RockHangerSurfaces.findRoute(this);
        nextSearch = tickCount + 30 + random.nextInt(16);
        if (route != null) {
            var mount = route.steps().getFirst();
            if (!RockHangerSurfaces.approachable(this, mount)
                    && !getNavigation().moveTo(RockHangerSurfaces.approachPath(this, mount), 1)) {
                route = null;
            }
        }
    }

    private boolean pursue(Player player) {
        boolean started = getNavigation().moveTo(player, 1.18);
        boolean advanced = chaseSample == null || position().distanceToSqr(chaseSample) > 0.12;
        stalledChaseTicks = started && advanced ? 0 : stalledChaseTicks + 10;
        chaseSample = position();
        nextSearch = tickCount + 10;
        if (stalledChaseTicks < 40) {
            return true;
        }
        // An unobstructed view does not mean the target is reachable from the cave floor.
        setTarget(null);
        getNavigation().stop();
        resetChase();
        retargetAfter = tickCount + 65;
        return false;
    }

    private void resetChase() {
        stalledChaseTicks = 0;
        chaseSample = null;
    }

    private boolean canHunt(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.level() == level();
    }

    private double climbSoundDistance;

    private void climb() {
        if (route == null || routeIndex >= route.steps().size()) {
            fall(null);
            return;
        }
        var step = route.steps().get(routeIndex);
        if (!RockHangerSurfaces.sturdy(level(), step.block(), step.support())
                || !RockHangerSurfaces.sweepClear(level(), getType(), position(), step.feet())) {
            fall(null);
            return;
        }
        selectClimbHeading(step);
        if (support() != step.support()) {
            setSupport(step.support());
            cornerTicks = 5;
        }
        if (cornerTicks > 0) {
            cornerTicks--;
            return;
        }
        Vec3 before = position();
        boolean reached = moveToward(step.feet(), 0.22);
        climbSoundDistance += before.distanceTo(position());
        if (climbSoundDistance >= 1.6) {
            climbSoundDistance -= 1.6;
            CreatureCue.HANGER_CLIMB.play(this, .48F);
        }
        if (!reached) {
            return;
        }
        if (++routeIndex == route.steps().size()) {
            BlockPos roof = route.anchor();
            route = null;
            if (RockHangerSurfaces.canHang(level(), roof, getType())) {
                restLength = 1 + random.nextInt(MAX_REST_LENGTH);
                attach(roof, Phase.LOWERING);
            } else {
                fall(null);
            }
        }
    }

    private void selectClimbHeading(RockHangerSurfaces.Step step) {
        Direction face = step.support();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 toward = step.feet().subtract(position());
        Vec3 tangent = toward.subtract(normal.scale(toward.dot(normal)));
        var previous = entityData.get(CLIMB_FORWARD);
        if (tangent.lengthSqr() < 1.e-5 && routeIndex + 1 < route.steps().size()) {
            var next = route.steps().get(routeIndex + 1);
            if (next.support() == face) {
                Vec3 ahead = next.feet().subtract(position());
                tangent = ahead.subtract(normal.scale(ahead.dot(normal)));
            }
        }
        if (tangent.lengthSqr() < 1.e-5) {
            // A waypoint reached along the face normal, such as the last lift onto the ceiling,
            // has no facing information. Keep the previous tangent when it still fits the face.
            Vec3 old = new Vec3(previous.x(), previous.y(), previous.z());
            tangent = old.subtract(normal.scale(old.dot(normal)));
            if (tangent.lengthSqr() < 1.e-5) {
                tangent = face.getAxis().isHorizontal() ? new Vec3(0, 1, 0)
                        : support().getAxis().isHorizontal() ? Vec3.atLowerCornerOf(support().getOpposite().getUnitVec3i())
                        : Vec3.directionFromRotation(0, getYRot());
            }
        }
        tangent = tangent.normalize();
        Vector3f direction = tangent.toVector3f();
        if (direction.dot(previous) < .9995F) {
            entityData.set(CLIMB_FORWARD, direction);
        }
        if (face == Direction.UP) {
            faceHorizontal(tangent, 180);
        }
    }

    private void faceHorizontal(Vec3 heading, float stepDegrees) {
        if (heading.horizontalDistanceSqr() > 1.e-5) {
            float yaw = (float) (Mth.atan2(-heading.x, heading.z) * Mth.RAD_TO_DEG);
            setYRot(Mth.approachDegrees(getYRot(), yaw, stepDegrees));
            yBodyRot = getYRot();
        }
    }

    private Vec3 nextPosition(Vec3 goal, double speed) {
        Vec3 delta = goal.subtract(position());
        double distance = delta.length();
        return distance <= speed ? goal : position().add(delta.scale(speed / distance));
    }

    private boolean moveToward(Vec3 goal, double speed) {
        Vec3 next = nextPosition(goal, speed);
        setDeltaMovement(Vec3.ZERO);
        setPos(next);
        return next.equals(goal);
    }

    private void hit(LivingEntity target, ResourceKey<DamageType> type, float easy, float normal, float hard) {
        float amount = switch (level().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> easy;
            case NORMAL -> normal;
            case HARD -> hard;
        };
        if (amount > 0) {
            var holder = level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type);
            target.hurtOrSimulate(new DamageSource(holder, this), amount);
        }
    }

    private static ResourceKey<DamageType> damageKey(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(ModIdentity.ID, path));
    }

    void spawnSuspended(BlockPos roof) {
        restLength = 1 + random.nextInt(MAX_REST_LENGTH);
        attach(roof, Phase.IDLE);
        setPos(idleFeet());
        startRest();
    }

    private void attach(BlockPos roof, Phase phase) {
        entityData.set(ANCHOR, Optional.of(roof.immutable()));
        setSupport(Direction.UP);
        changePhase(phase);
    }

    private void fall(@Nullable Player cutter) {
        entityData.set(ANCHOR, Optional.empty());
        route = null;
        setTarget(cutter != null && canHunt(cutter) ? cutter : null);
        unseenTicks = 0;
        resetChase();
        changePhase(Phase.FALLING);
    }

    private void setSupport(Direction side) {
        entityData.set(SUPPORT, side.get3DDataValue());
    }

    private void changePhase(Phase phase) {
        if (phase != Phase.SLIPPING && phase() != Phase.SLIPPING) {
            slippedThisReel = false;
        }
        entityData.set(PHASE, phase.ordinal());
        phaseTicks = 0;
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(switch (phase) {
            case GROUND, BITING, FALLING -> false;
            default -> true;
        });
    }

    @Override
    public boolean causeFallDamage(double distance, float modifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return anchor().isEmpty() && phase() != Phase.CLIMBING && super.isPushable();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (phase() == Phase.FEEDING && !isTuftClipped() && player.getItemInHand(hand).is(Items.SHEARS)) {
            if (!level().isClientSide()) {
                shear(player, hand, false);
            }
            return (level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
        }
        return super.mobInteract(player, hand);
    }

    void shear(Player player, InteractionHand hand, boolean tether) {
        if (!(level() instanceof ServerLevel level) || !player.getItemInHand(hand).is(Items.SHEARS)
                || (tether ? !hasShearableTether() : phase() != Phase.FEEDING || isTuftClipped())) {
            return;
        }
        if (phase() != Phase.FEEDING || !isTuftClipped()) {
            spawnAtLocation(level, new ItemStack(ModItems.ROCK_SILK.get(), tether ? 1 : 2));
        }
        entityData.set(TUFT_CLIPPED, true);
        player.getItemInHand(hand).hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        playSound(SoundEvents.SHEEP_SHEAR, 0.8F, 0.8F);
        if (tether) {
            fall(player);
        }
    }

    boolean hasShearableTether() {
        return isAlive() && anchor().isPresent() && phase() != Phase.BRACED
                && silkTop().distanceToSqr(silkBottom()) > 0.16;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        var tag = new CompoundTag();
        anchor().ifPresent(pos -> tag.putLong("SilkAnchor", pos.asLong()));
        tag.putString("RockPhase", phase().name());
        tag.putInt("SupportFace", support().get3DDataValue());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("RestLength", restLength);
        tag.putBoolean("TuftClipped", isTuftClipped());
        SaveTag.write(output, tag);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        var tag = SaveTag.read(input);
        setSupport(tag.contains("SupportFace") ? Direction.from3DDataValue(tag.getIntOr("SupportFace", 0)) : Direction.DOWN);
        entityData.set(TUFT_CLIPPED, tag.getBooleanOr("TuftClipped", false));
        restLength = tag.contains("RestLength") ? Mth.clamp(tag.getIntOr("RestLength", 0), 1, MAX_REST_LENGTH) : MAX_REST_LENGTH;
        nextLengthChange = tickCount + 100 + random.nextInt(141);
        Phase saved = tag.contains("RockPhase") ? Phase.valueOf(tag.getStringOr("RockPhase", "")) : Phase.GROUND;
        if (tag.contains("SilkAnchor")) {
            Phase restored = switch (saved) {
                case IDLE, FEEDING, LOWERING, DEFENSE_REEL, BRACED -> saved;
                default -> Phase.RETRACTING;
            };
            attach(BlockPos.of(tag.getLongOr("SilkAnchor", 0L)), restored);
            phaseTicks = tag.getIntOr("PhaseTicks", 0);
        } else {
            // A partial surface route may no longer be valid after its chunks have been reloaded.
            changePhase(saved == Phase.GROUND || saved == Phase.BITING ? Phase.GROUND : Phase.FALLING);
        }
    }
}


package yagen.waitmydawn.yagens_mobs.entity.vulture;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModSounds;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationData;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestEntity;

public final class Vulture extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> JUVENILE =
            SynchedEntityData.defineId(Vulture.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Vulture.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockState> CARRIED =
            SynchedEntityData.defineId(Vulture.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Byte> CARGO_DISPLAY =
            SynchedEntityData.defineId(Vulture.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CARRYING_MAIL =
            SynchedEntityData.defineId(Vulture.class, EntityDataSerializers.BOOLEAN);
    private static final byte GLIDING = 0;
    private static final byte PERCHED = 1;
    private static final byte DIVING = 2;
    private static final byte HAULING = 3;
    private static final byte TAKING_OFF = 4;
    private static final byte FLAPPING = 5;
    private static final byte LANDING = 6;
    private static final byte HAULING_GLIDE = 7;
    private static final int TAKEOFF_TICKS = 13;
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.condroc.glide");
    private static final RawAnimation PERCH = RawAnimation.begin().thenLoop("animation.condroc.perch");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.condroc.dive");
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop("animation.condroc.carry");
    private static final RawAnimation CARGO_GLIDE = RawAnimation.begin().thenLoop("animation.condroc.cargo_glide");
    private static final RawAnimation TAKEOFF = RawAnimation.begin().thenPlayAndHold("animation.condroc.takeoff");
    private static final RawAnimation FLAP = RawAnimation.begin().thenLoop("animation.condroc.flap");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlayAndHold("animation.condroc.land");
    private static final int GROWTH_TICKS = 24000;
    private static final int PERCH_FEED_TICKS = 12000;
    private static final int MEAL_REGENERATION_TICKS = 15 * 20;
    private static final int ADULT_PATROL_CLEARANCE = 8;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos claimedPost;
    @Nullable private BlockPos treePerch;
    @Nullable private UUID shipping;
    @Nullable private Vulture mate;
    @Nullable private ItemEntity food;
    @Nullable private Vec3 wander;
    private int growth;
    private int nextFoodScan;
    private int nextPerchScan;
    private int nextTreeScan = 120;
    private int nextHuntScan;
    private int nextMateScan;
    private int nextWander;
    private long readyUntil;
    private long breedCooldown;
    private long nextMeal;
    private long mailRestUntil;
    private long recoverUntil;
    private double escapeHeight = Double.NEGATIVE_INFINITY;
    private int perchApproachTicks;
    private int treeApproachTicks;
    private long treeRestUntil;
    private int takeoffUntil;

    public Vulture(EntityType<? extends Vulture> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, .24).add(Attributes.FLYING_SPEED, .30);
    }

    public static boolean checkSpawnPlacement(EntityType<Vulture> type, ServerLevelAccessor level,
                                              MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!ServerConfigs.VULTURE.canSpawn() || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())
                || !level.canSeeSky(pos)) return false;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (pos.getY() < surfaceY || pos.getY() > surfaceY + 3) return false;
        BlockPos floor = pos.atY(surfaceY - 1);
        BlockState support = level.getBlockState(floor);
        if (!support.getFluidState().isEmpty() || !(support.isFaceSturdy(level, floor, Direction.UP)
                || support.is(net.minecraft.tags.BlockTags.LEAVES))) return false;
        if (level.getBiome(pos).is(ModTags.VULTURE_HABITAT)) {
            return true;
        }
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Direction side = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            BlockPos first = pos.relative(side, 9);
            BlockPos other = pos.relative(side.getOpposite(), 9);
            if (level.hasChunk(first.getX() >> 4, first.getZ() >> 4)
                    && level.hasChunk(other.getX() >> 4, other.getZ() >> 4)
                    && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, first.getX(), first.getZ()) >= pos.getY() + 8
                    && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, other.getX(), other.getZ()) >= pos.getY() + 8) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CreatureCue.CONDROC_HURT.orElse(super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CreatureCue.CONDROC_DEATH.orElse(super.getDeathSound());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.VULTURE.maxGroupCount();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group) {
        var previous = super.finalizeSpawn(level, difficulty, reason, group);
        if ((reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION)
                && random.nextDouble() < ServerConfigs.VULTURE_JUVENILE_CHANCE.get()) {
            setBaby(true);
        }
        return previous;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isBaby() ? CreatureCue.CONDROC_CHICK.orElse(ModSounds.VULTURE_CHICK.get())
                : CreatureCue.CONDROC_ADULT.orElse(super.getAmbientSound());
    }

    @Override
    public int getAmbientSoundInterval() {
        return isBaby() ? 260 : super.getAmbientSoundInterval();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JUVENILE, false);
        builder.define(MODE, GLIDING);
        builder.define(CARRIED, Blocks.AIR.defaultBlockState());
        builder.define(CARRYING_MAIL, false);
        builder.define(CARGO_DISPLAY, (byte) 0);
    }

    @Override
    public boolean isBaby() {
        return entityData.get(JUVENILE);
    }

    @Override
    public void setBaby(boolean baby) {
        growth = baby ? GROWTH_TICKS : 0;
        entityData.set(JUVENILE, baby);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (JUVENILE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, state -> state.setAndContinue(
                switch (entityData.get(MODE)) {
                    case PERCHED -> PERCH;
                    case DIVING -> DIVE;
                    case HAULING -> CARRY;
                    case TAKING_OFF -> TAKEOFF;
                    case FLAPPING -> FLAP;
                    case LANDING -> LAND;
                    case HAULING_GLIDE -> CARGO_GLIDE;
                    default -> GLIDE;
                })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isBaby() && target instanceof Zombie && super.canAttack(target);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (shipping != null || !stack.is(ModTags.VULTURE_FOOD)) {
            return super.mobInteract(player, hand);
        }
        if (level() instanceof ServerLevel server) {
            eat(server, stack.consumeAndReturn(1, player));
            setPersistenceRequired();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void customServerAiStep() {
        ServerLevel level = (ServerLevel) level();
        if (growth > 0 && --growth == 0) {
            setBaby(false);
        }
        var stations = StationData.get(level);
        if (shipping != null) {
            if (stations.hasFlight(shipping)) {
                return;
            }
            finishShipment(shipping);
        }
        if (hunt(level, stations) || breed(level, stations) || forage(level, stations)) {
            return;
        }
        if (!isBaby() && (perch(level, stations) || restInTree(level))) {
            return;
        }
        roam(level);
    }

    @Override
    public void travel(Vec3 input) {
        if (isNoAi()) {
            super.travel(input);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    private boolean hunt(ServerLevel level, StationData stations) {
        if (isBaby()) {
            return false;
        }
        LivingEntity target = getTarget();
        if (target != null && (!canAttack(target) || !target.isAlive() || distanceToSqr(target) > 36 * 36)) {
            setTarget(null);
            target = null;
        }
        if (target == null && tickCount >= nextHuntScan) {
            nextHuntScan = tickCount + 18;
            target = level.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(22, 20, 22), this::canAttack)
                    .stream().filter(this::hasLineOfSight).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (target != null) {
                setTarget(target);
                target = getTarget();
            }
        }
        if (level.getGameTime() < recoverUntil || getY() < escapeHeight) {
            flyToward(position().add(0, Math.max(0.5, escapeHeight - getY()), 0), .40, GLIDING);
            if (getY() >= escapeHeight && level.getGameTime() >= recoverUntil) {
                escapeHeight = Double.NEGATIVE_INFINITY;
            }
            return true;
        }
        if (target == null) {
            return false;
        }
        releaseSeat(stations);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(0.0, 0.2, 0.0);
        boolean diving = entityData.get(MODE) == DIVING;
        if (!diving && (getY() < aim.y + 5.0 || horizontalDistanceTo(aim) > 5.0)) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getBlockX(), target.getBlockZ());
            flyToward(new Vec3(aim.x, Math.max(aim.y + 6.0, ground + 5), aim.z), .27, GLIDING);
            return true;
        }
        if (!hasLineOfSight(target)) {
            escapeHeight = getY() + 5;
            recoverUntil = level.getGameTime() + 18;
            setTarget(null);
            return true;
        }
        if (distanceToSqr(aim) <= 1.75 * 1.75) {
            doHurtTarget(target);
            escapeHeight = aim.y + 7.0;
            recoverUntil = level.getGameTime() + 20;
            flyToward(position().add(0, 8, 0), .43, GLIDING);
            return true;
        }
        flyToward(aim, .43, DIVING);
        return true;
    }

    private boolean forage(ServerLevel level, StationData stations) {
        if (food != null && (!food.isAlive() || distanceToSqr(food) > 28 * 28)) {
            food = null;
        }
        if (food == null && tickCount >= nextFoodScan) {
            nextFoodScan = tickCount + 28 + random.nextInt(10);
            food = level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(24, 24, 24),
                            item -> item.isAlive() && item.getItem().is(ModTags.VULTURE_FOOD)
                                    && distanceToSqr(item) <= 24 * 24)
                    .stream().filter(this::hasLineOfSight).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        if (food == null) {
            return false;
        }
        releaseSeat(stations);
        if (distanceToSqr(food) <= 1.15 && hasLineOfSight(food)) {
            var remainder = food.getItem().copy();
            ItemStack bite = remainder.split(1);
            if (remainder.isEmpty()) {
                food.discard();
            } else {
                food.setItem(remainder);
            }
            food = null;
            nextFoodScan = tickCount + 55;
            eat(level, bite);
        } else {
            flyToward(food.position().add(0.0, .15, 0.0), .27, GLIDING);
        }
        return true;
    }

    private void eat(ServerLevel level, ItemStack stack) {
        if (!isBaby() && stack.is(Items.ROTTEN_FLESH) && level.getGameTime() >= breedCooldown) {
            readyUntil = level.getGameTime() + 1200;
            level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + .25, getZ(), 4, .22, .12, .22, 0);
        }
        finishMeal(level, stack);
    }

    private void finishMeal(ServerLevel level, ItemStack stack) {
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEAL_REGENERATION_TICKS));
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), getX(), getEyeY(), getZ(),
                5, .18, .10, .18, .03);
        CreatureCue.CONDROC_EAT.playReplacing(this, .65F, SoundEvents.FOX_EAT, .80F);
    }

    private boolean ready(ServerLevel level) {
        return isAlive() && !isBaby() && shipping == null && level.getGameTime() < readyUntil
                && level.getGameTime() >= breedCooldown;
    }

    private boolean breed(ServerLevel level, StationData stations) {
        if (!ready(level)) {
            mate = null;
            return false;
        }
        if (mate != null && (!mate.ready(level) || mate.mate != this || distanceToSqr(mate) > 16 * 16)) {
            mate = null;
        }
        if (mate == null && tickCount >= nextMateScan) {
            nextMateScan = tickCount + 20;
            mate = level.getEntitiesOfClass(Vulture.class, getBoundingBox().inflate(16),
                            bird -> bird != this && bird.mate == null && bird.ready(level) && distanceToSqr(bird) <= 16 * 16)
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (mate != null) {
                mate.mate = this;
            }
        }
        if (mate == null) {
            return false;
        }
        releaseSeat(stations);
        Vec3 midpoint = position().lerp(mate.position(), .5);
        BlockPos nest = freeNestSite(level, BlockPos.containing(midpoint));
        if (nest == null) {
            flyToward(midpoint, .22, GLIDING);
            return true;
        }
        Vec3 approach = Vec3.atBottomCenterOf(nest).add(0, .5, 0);
        if (distanceToSqr(approach) > 4 || mate.distanceToSqr(approach) > 9) {
            flyToward(approach, .24, GLIDING);
            return true;
        }
        BlockState state = ModBlocks.VULTURE_NEST.get().defaultBlockState().setValue(VultureNestBlock.EGGS, 1 + random.nextInt(3));
        if (level.setBlock(nest, state, Block.UPDATE_ALL) && level.getBlockEntity(nest) instanceof VultureNestEntity eggNest) {
            eggNest.beginIncubation(level);
            long cooldown = level.getGameTime() + 6000;
            readyUntil = 0;
            mate.readyUntil = 0;
            breedCooldown = cooldown;
            mate.breedCooldown = cooldown;
            mate.mate = null;
            mate = null;
        }
        return true;
    }

    private @Nullable BlockPos freeNestSite(ServerLevel level, BlockPos center) {
        for (BlockPos at : BlockPos.spiralAround(center, 2, Direction.EAST, Direction.SOUTH)) {
            if (!level.hasChunkAt(at)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            if (level.getBlockState(surface).isAir() && level.getBlockState(surface.above()).isAir()
                    && ModBlocks.VULTURE_NEST.get().defaultBlockState().canSurvive(level, surface)) {
                return surface.immutable();
            }
        }
        return null;
    }

    private boolean perch(ServerLevel level, StationData stations) {
        if (claimedPost == null && tickCount >= nextPerchScan) {
            nextPerchScan = tickCount + 42 + random.nextInt(18);
            stations.offerPerch(level, this);
        }
        if (claimedPost == null) {
            return false;
        }
        var station = stations.station(claimedPost);
        boolean restingAfterDelivery = level.getGameTime() < mailRestUntil;
        if (station == null || !stations.ownsPerch(claimedPost, getUUID())
                || !restingAfterDelivery && stations.fuel(station) == 0) {
            releaseSeat(stations);
            return false;
        }
        Vec3 stand = StationData.perchPoint(claimedPost);
        if (settleAt(level, stand)) {
            if (nextMeal == 0) {
                nextMeal = level.getGameTime() + PERCH_FEED_TICKS;
            } else if (!restingAfterDelivery && level.getGameTime() >= nextMeal && stations.spendFuel(station, 1)) {
                finishMeal(level, new ItemStack(Items.ROTTEN_FLESH));
                nextMeal = level.getGameTime() + PERCH_FEED_TICKS;
                if (stations.fuel(station) == 0) {
                    releaseSeat(stations);
                    return false;
                }
            }
            setFlightMode(PERCHED);
            perchApproachTicks = 0;
        } else if (++perchApproachTicks > 240) {
            releaseSeat(stations);
            return false;
        } else {
            Vec3 approach;
            if (getY() < stand.y - .04) {
                // Reach beam height from the open side before crossing over the collision shape.
                Vec3 perpendicular = station.axis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
                double side = position().subtract(stand).dot(perpendicular);
                if (Math.abs(side) < .77) {
                    Vec3 edge = stand.add(perpendicular.scale(Math.copySign(.92, side)));
                    Vec3 escape = new Vec3(edge.x - getX(), 0, edge.z - getZ());
                    moveFlight(limited(escape, .23), escape, GLIDING);
                    return true;
                }
                approach = position().add(0, stand.y + .48 - getY(), 0);
            } else {
                approach = horizontalDistanceTo(stand) > 1 ? stand.add(0, .55, 0) : stand;
            }
            boolean finalApproach = getY() >= stand.y - .04 && horizontalDistanceTo(stand) <= 1.65
                    && getY() <= stand.y + 1.45;
            flyToward(approach, finalApproach ? .17 : .23, finalApproach ? LANDING : GLIDING);
        }
        return true;
    }

    private boolean restInTree(ServerLevel level) {
        if (treePerch == null && tickCount >= nextTreeScan) {
            nextTreeScan = tickCount + 160 + random.nextInt(80);
            treePerch = findTreePerch(level);
        }
        if (treePerch == null) {
            return false;
        }
        Vec3 stand = Vec3.atBottomCenterOf(treePerch.above());
        if (!canUseTreePerch(level, treePerch) || treeRestUntil != 0 && level.getGameTime() >= treeRestUntil) {
            leaveTreePerch();
            return false;
        }
        if (settleAt(level, stand)) {
            if (treeRestUntil == 0) {
                treeRestUntil = level.getGameTime() + 600 + random.nextInt(601);
            }
            treeApproachTicks = 0;
            setFlightMode(PERCHED);
            return true;
        }
        if (++treeApproachTicks > 180) {
            leaveTreePerch();
            return false;
        }

        double horizontal = horizontalDistanceTo(stand);
        if (getY() < stand.y - .04 && horizontal < 4.5) {
            // Climb alongside the canopy before crossing over the chosen branch.
            Vec3 away = position().subtract(stand).multiply(1, 0, 1).normalize();
            if (away.lengthSqr() < .5) {
                away = new Vec3(1, 0, 0);
            }
            flyToward(position().add(away), .22, GLIDING);
        } else {
            boolean landing = horizontal <= 1.65 && getY() >= stand.y - .04 && getY() <= stand.y + 1.45;
            flyToward(horizontal > 1.0 ? stand.add(0, 1.15, 0) : stand, landing ? .17 : .22,
                    landing ? LANDING : GLIDING);
        }
        return true;
    }

    private @Nullable BlockPos findTreePerch(ServerLevel level) {
        for (BlockPos column : BlockPos.spiralAround(blockPosition(), 14, Direction.EAST, Direction.SOUTH)) {
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos support = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            if (canUseTreePerch(level, support) && level.getEntitiesOfClass(Vulture.class, new AABB(support).inflate(18),
                    other -> other != this && support.equals(other.treePerch)).isEmpty()) {
                return support.immutable();
            }
        }
        return null;
    }

    private boolean canUseTreePerch(ServerLevel level, BlockPos support) {
        Vec3 stand = Vec3.atBottomCenterOf(support.above());
        if (!level.hasChunkAt(support) || Math.abs(stand.y - getY()) > 16) {
            return false;
        }
        BlockPos trunk = support;
        // Natural acacias can cover the branch tip with up to two layers of foliage.
        for (int i = 0; i < 2 && level.getBlockState(trunk).is(Blocks.ACACIA_LEAVES); i++) {
            trunk = trunk.below();
        }
        return level.getBlockState(trunk).is(Blocks.ACACIA_LOG)
                && level.noCollision(this, getBoundingBox().move(stand.subtract(position())));
    }

    private boolean settleAt(ServerLevel level, Vec3 stand) {
        Vec3 correction = stand.subtract(position());
        if (correction.lengthSqr() > .23 * .23 || !level.noCollision(this, getBoundingBox().move(correction))) {
            return false;
        }
        setPos(stand.x, stand.y, stand.z);
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private void leaveTreePerch() {
        treePerch = null;
        treeRestUntil = 0;
        treeApproachTicks = 0;
        nextTreeScan = tickCount + 180 + random.nextInt(160);
    }

    private void roam(ServerLevel level) {
        boolean juvenile = isBaby();
        var heightmap = juvenile ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.MOTION_BLOCKING;
        if (wander == null || tickCount >= nextWander || distanceToSqr(wander) < 1) {
            int x = getBlockX() + random.nextInt(23) - 11;
            int z = getBlockZ() + random.nextInt(23) - 11;
            if (!level.hasChunkAt(new BlockPos(x, getBlockY(), z))) {
                x = getBlockX();
                z = getBlockZ();
            }
            int surface = level.getHeight(heightmap, x, z);
            double clearance = juvenile ? 1.5 : ADULT_PATROL_CLEARANCE + random.nextInt(5);
            wander = new Vec3(x + .5, surface + clearance, z + .5);
            nextWander = tickCount + 90;
        }
        if (juvenile) {
            flyToward(wander, .16, GLIDING);
            return;
        }
        var forward = limited(wander.subtract(position()).multiply(1, 0, 1), 2);
        BlockPos ahead = BlockPos.containing(position().add(forward));
        int surface = level.getHeight(heightmap, getBlockX(), getBlockZ());
        if (level.hasChunkAt(ahead)) {
            surface = Math.max(surface, level.getHeight(heightmap, ahead.getX(), ahead.getZ()));
        }
        double minimumY = surface + ADULT_PATROL_CLEARANCE;
        Vec3 aim = getY() < minimumY
                ? new Vec3(getX(), minimumY, getZ())
                : new Vec3(wander.x, Math.max(wander.y, minimumY), wander.z);
        flyToward(aim, .22, GLIDING);
    }

    private double horizontalDistanceTo(Vec3 point) {
        return Math.hypot(point.x - getX(), point.z - getZ());
    }

    private void flyToward(Vec3 aim, double speed, byte mode) {
        Vec3 direction = aim.subtract(position());
        Vec3 step = limited(direction, speed);
        if (mode != DIVING && !level().noCollision(this, getBoundingBox().move(step))) {
            step = new Vec3(0.0, speed, 0.0);
            mode = GLIDING;
        }
        moveFlight(step, direction, mode);
    }

    public void flyCargo(Vec3 aim, double speed, Vec3 facing, boolean flapping) {
        Vec3 shift = aim.subtract(position());
        boolean loaded = !carriedBlock().isAir() || isCarryingMail();
        byte pose = loaded ? (flapping ? HAULING : HAULING_GLIDE) : (flapping ? FLAPPING : GLIDING);
        moveFlight(limited(shift, speed), facing, pose);
    }

    private static Vec3 limited(Vec3 velocity, double speed) {
        return velocity.lengthSqr() > speed * speed ? velocity.normalize().scale(speed) : velocity;
    }

    private void moveFlight(Vec3 step, Vec3 look, byte mode) {
        if (entityData.get(MODE) == PERCHED) {
            takeoffUntil = tickCount + TAKEOFF_TICKS;
        }
        setFlightMode(flightPose(step, mode));
        setDeltaMovement(Vec3.ZERO);
        if (look.horizontalDistanceSqr() > .0001) {
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG - 90.0);
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
        move(MoverType.SELF, step);
        fallDistance = 0;
    }

    private void setFlightMode(byte mode) {
        byte previous = entityData.get(MODE);
        if (previous == mode) return;
        entityData.set(MODE, mode);
        if (mode == PERCHED) {
            CreatureCue.CONDROC_PERCH.play(this, .65F);
        } else if (mode == DIVING) {
            CreatureCue.CONDROC_DIVE.play(this, .8F);
        } else if ((mode == TAKING_OFF || mode == FLAPPING || mode == HAULING)
                && previous != TAKING_OFF && previous != FLAPPING && previous != HAULING) {
            CreatureCue.CONDROC_FLAP.play(this, .75F);
        }
    }

    private byte flightPose(Vec3 step, byte requested) {
        if (requested == LANDING || requested == DIVING) {
            takeoffUntil = tickCount;
            return requested;
        }
        if (tickCount < takeoffUntil) {
            return TAKING_OFF;
        }
        if (shipping != null) {
            return requested;
        }
        int stroke = Math.floorMod(tickCount + getId() * 11, step.y > .085 ? 72 : 200);
        return stroke < 16 ? FLAPPING : GLIDING;
    }

    public void claimPerch(BlockPos pos) {
        if (treePerch != null) {
            leaveTreePerch();
        }
        claimedPost = pos.immutable();
        nextMeal = 0;
        mailRestUntil = 0;
        perchApproachTicks = 0;
        setPersistenceRequired();
    }

    public void allowMailRest(long now) {
        mailRestUntil = now + 1200;
    }

    public CompoundTag saveForMail() {
        releaseSeat(StationData.get((ServerLevel) level()));
        return saveWithoutId(new CompoundTag());
    }

    public boolean isReadyToCarry(BlockPos pos) {
        return isAlive() && !isBaby() && shipping == null && pos.equals(claimedPost)
                && entityData.get(MODE) == PERCHED && distanceToSqr(StationData.perchPoint(pos)) < 1.0e-6;
    }

    public boolean isApproachingPerch() {
        return isAlive() && claimedPost != null && !isReadyToCarry(claimedPost);
    }

    private void releaseSeat(StationData stations) {
        if (treePerch != null) {
            leaveTreePerch();
        }
        if (claimedPost != null) {
            stations.releasePerch(claimedPost, getUUID());
            claimedPost = null;
            mailRestUntil = 0;
            nextPerchScan = tickCount + 30;
        }
    }

    public void beginShipment(UUID id, BlockState state, CargoDisplay display) {
        releaseSeat(StationData.get((ServerLevel) level()));
        shipping = id;
        setPersistenceRequired();
        setTarget(null);
        entityData.set(CARRIED, state);
        entityData.set(CARGO_DISPLAY, (byte) display.ordinal());
        if (entityData.get(MODE) == PERCHED) {
            takeoffUntil = tickCount + TAKEOFF_TICKS;
            setFlightMode(TAKING_OFF);
        } else {
            entityData.set(MODE, HAULING_GLIDE);
        }
    }

    public BlockState carriedBlock() {
        return entityData.get(CARRIED);
    }

    public void beginMailShipment(UUID id) {
        beginShipment(id, Blocks.AIR.defaultBlockState(), CargoDisplay.SINGLE);
        entityData.set(CARRYING_MAIL, true);
    }

    public boolean isCarryingMail() {
        return entityData.get(CARRYING_MAIL);
    }

    public CargoDisplay cargoDisplay() {
        return switch (entityData.get(CARGO_DISPLAY)) {
            case 1 -> CargoDisplay.DOUBLE_CHEST;
            case 2 -> CargoDisplay.PARTNER;
            default -> CargoDisplay.SINGLE;
        };
    }

    public void clearCarriedBlock() {
        entityData.set(CARRIED, Blocks.AIR.defaultBlockState());
        entityData.set(CARGO_DISPLAY, (byte) 0);
        entityData.set(CARRYING_MAIL, false);
    }

    public enum CargoDisplay { SINGLE, DOUBLE_CHEST, PARTNER }

    public void finishShipment(UUID id) {
        if (id.equals(shipping)) {
            shipping = null;
            clearCarriedBlock();
            entityData.set(MODE, GLIDING);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            var stations = StationData.get(server);
            releaseSeat(stations);
            if (shipping != null) {
                stations.birdKilled(shipping, blockPosition());
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Growth", growth);
        tag.putLong("ReadyUntil", readyUntil);
        tag.putLong("BreedCooldown", breedCooldown);
        if (claimedPost != null) {
            tag.putLong("Perch", claimedPost.asLong());
            tag.putLong("NextMeal", nextMeal);
            tag.putLong("MailRestUntil", mailRestUntil);
        }
        if (treePerch != null) {
            tag.putLong("TreePerch", treePerch.asLong());
            tag.putLong("TreeRestUntil", treeRestUntil);
        }
        if (shipping != null) {
            tag.putUUID("Shipment", shipping);
            tag.put("Carried", NbtUtils.writeBlockState(carriedBlock()));
            tag.putByte("CargoDisplay", entityData.get(CARGO_DISPLAY));
            tag.putBoolean("CarryingMail", isCarryingMail());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        growth = Math.max(0, tag.getInt("Growth"));
        entityData.set(JUVENILE, growth > 0);
        readyUntil = tag.getLong("ReadyUntil");
        breedCooldown = tag.getLong("BreedCooldown");
        if (tag.contains("Perch")) {
            claimedPost = BlockPos.of(tag.getLong("Perch"));
            nextMeal = tag.getLong("NextMeal");
            mailRestUntil = tag.getLong("MailRestUntil");
        }
        if (tag.contains("TreePerch")) {
            treePerch = BlockPos.of(tag.getLong("TreePerch"));
            treeRestUntil = tag.getLong("TreeRestUntil");
        }
        if (tag.hasUUID("Shipment")) {
            shipping = tag.getUUID("Shipment");
            entityData.set(CARRIED, NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getCompound("Carried")));
            entityData.set(CARGO_DISPLAY, tag.getByte("CargoDisplay"));
            entityData.set(CARRYING_MAIL, tag.getBoolean("CarryingMail"));
            entityData.set(MODE, carriedBlock().isAir() && !isCarryingMail() ? GLIDING : HAULING_GLIDE);
        }
        setNoGravity(true);
    }
}

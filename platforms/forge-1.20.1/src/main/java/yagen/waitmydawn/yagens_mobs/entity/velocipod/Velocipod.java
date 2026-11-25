package yagen.waitmydawn.yagens_mobs.entity.velocipod;

import yagen.waitmydawn.yagens_mobs.entity.PetHazards;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.compat.LegacyItemData;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.mixin.LivingEntityJumpInput;

public final class Velocipod extends PathfinderMob implements GeoEntity, FlyingAnimal, HasCustomInventoryScreen {
    @Override
    public void move(MoverType kind, Vec3 motion) {
        if (kind == MoverType.SELF && level() instanceof ServerLevel
                && isTamed() && !isPassenger() && !isVehicle()) {
            Vec3 safeMotion = PetHazards.steer(this, motion, true, false);
            if (safeMotion != motion) {
                motion = safeMotion;
                setDeltaMovement(safeMotion);
            }
        }
        super.move(kind, motion);
    }

    public static final float MODEL_SCALE = 1.5F;
    public static final float JUVENILE_DIMENSIONS = .59F;
    public static final float SEAT_HEIGHT = 19.4F;
    public static final float SEAT_BACK = 1.07F;
    private static final double RIDDEN_GROUND_SPEED = 5.0 / 20;
    private static final double RIDDEN_FLIGHT_SPEED = 12.0 / 20;
    private static final double RIDDEN_SPRINT_SPEED = 36.0 / 20;
    private static final float RIDDEN_TURN_STEP = 13;
    private static final double AI_FLIGHT_SPEED = 6.0 / 20;
    private static final double AI_SPRINT_SPEED = 12.0 / 20;
    private static final double IDLE_SPEED = 2.0 / 20;
    private static final int STAMINA_MAX = 10_000;
    private static final int STAMINA_TO_SPRINT = 3_000;
    private static final int CANE_RESTORE = 5_000;
    private static final int SPRINT_INPUT_TIMEOUT = 18;
    private static final int GROWTH_TICKS = 20 * 60 * 20;
    private static final int SLIME_GROWTH_TICKS = 30 * 20;
    private static final int BROOD_TICKS = 60 * 20;
    private static final long BROOD_COOLDOWN_TICKS = 20L * 60 * 20;
    private static final ResourceKey<DamageType> BITE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ModIdentity.ID, "velocipod_bite"));
    private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLIGHT = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> BROODING = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> STAMINA = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> RIDE_MOTION = SynchedEntityData.defineId(Velocipod.class,
            EntityDataSerializers.VECTOR3);
    private static final Flight[] FLIGHTS = Flight.values();
    private final AnimatableInstanceCache animation = GeckoLibUtil.createInstanceCache(this);
    private final yagen.waitmydawn.yagens_mobs.compat.LegacyContainer backpack = new yagen.waitmydawn.yagens_mobs.compat.LegacyContainer(27) {
        @Override
        public boolean stillValid(Player player) {
            return canOpenBackpack(player);
        }
    };
    private boolean sprintRequested;
    private int sprintInputAt;
    private boolean aiSprint;
    private boolean sprintingThisTick;
    private final Vector3f previousRideMotion = new Vector3f();
    private final Vector3f rideMotion = new Vector3f();
    private int growth;
    private int broodRemaining;
    private long broodReadyAt;
    private boolean replantCane;
    private int flightTicks;
    private int attackCooldown;
    private double nextY = Double.NaN;
    private Vec3 destination = Vec3.ZERO;

    public Velocipod(EntityType<? extends Velocipod> type, Level level) {
        super(type, level);
        moveControl = new FlightControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 80).add(Attributes.ARMOR, 8)
                .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.FOLLOW_RANGE, 30)
                .add(Attributes.MOVEMENT_SPEED, .28).add(Attributes.FLYING_SPEED, .66);
    }

    public static boolean checkSpawnPlacement(EntityType<Velocipod> type, ServerLevelAccessor level,
                                               MobSpawnType reason, BlockPos pos, RandomSource random) {
        return ServerConfigs.VELOCIPOD.canSpawn() && level.canSeeSky(pos)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.VELOCIPOD.maxGroupCount();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group, @Nullable CompoundTag dataTag) {
        setColor(Color.random(random));
        return super.finalizeSpawn(level, difficulty, reason, group, dataTag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BABY, false);
        entityData.define(COLOR, Color.PURPLE.ordinal());
        entityData.define(FLIGHT, (byte) Flight.GROUND.ordinal());
        entityData.define(BROODING, false);
        entityData.define(OWNER, Optional.empty());
        entityData.define(STAMINA, STAMINA_MAX);
        entityData.define(RIDE_MOTION, new Vector3f());
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(0, new VelocipodGoals.Assist(this));
        goalSelector.addGoal(0, new VelocipodGoals.Land(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Slime.class, 10, true, false,
                target -> target instanceof Slime slime && hunts(slime) && !isBrooding()));
        goalSelector.addGoal(1, new VelocipodGoals.Hunt(this));
        goalSelector.addGoal(2, new VelocipodGoals.Brood(this));
        goalSelector.addGoal(3, new VelocipodGoals.SugarCane(this));
        goalSelector.addGoal(4, new VelocipodGoals.Patrol(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        var flying = new FlyingPathNavigation(this, level);
        flying.setCanOpenDoors(false);
        flying.setCanFloat(true);
        return flying;
    }

    @Override
    public boolean isBaby() {
        return entityData.get(BABY);
    }

    @Override
    public void setBaby(boolean baby) {
        if (isBaby() != baby) {
            float healthFraction = getHealth() / getMaxHealth();
            growth = baby ? GROWTH_TICKS : 0;
            entityData.set(BABY, baby);
            updateAgeAttributes();
            setHealth(healthFraction * getMaxHealth());
        }
    }

    private void updateAgeAttributes() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(isBaby() ? 40 : 80);
        getAttribute(Attributes.ARMOR).setBaseValue(isBaby() ? 0 : 8);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        var dimensions = super.getDimensions(pose);
        return isBaby() ? dimensions.scale(JUVENILE_DIMENSIONS) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BABY.equals(key)) {
            refreshDimensions();
        }
    }

    public Color color() {
        return Color.VALUES[entityData.get(COLOR)];
    }

    public void setColor(Color color) {
        entityData.set(COLOR, color.ordinal());
    }

    public Flight flight() {
        return FLIGHTS[entityData.get(FLIGHT)];
    }

    @Override
    public boolean isFlying() {
        return flight() != Flight.GROUND && !onGround();
    }

    public boolean isBrooding() {
        return entityData.get(BROODING);
    }

    int broodTicks() {
        return broodRemaining;
    }

    void setBroodTicks(int ticks) {
        broodRemaining = ticks;
        entityData.set(BROODING, ticks > 0);
    }

    void completeBrood() {
        broodReadyAt = level().getGameTime() + BROOD_COOLDOWN_TICKS;
        setBroodTicks(0);
    }

    boolean hasCaneToPlant() {
        return replantCane;
    }

    void setCaneToPlant(boolean plant) {
        replantCane = plant;
    }

    public boolean hunts(Slime slime) {
        return slime.getType() == EntityType.SLIME && (!isBaby() || slime.getSize() <= 2 && slime.getMaxHealth() <= 4);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isVehicle() && !isOwnedBy(target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (!(level() instanceof ServerLevel level) || !(entity instanceof LivingEntity target) || !canAttack(target)) {
            return false;
        }
        updateAttackDamage();
        var bite = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BITE), this);
        if (!target.hurt(bite, (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            return false;
        }
        setLastHurtMob(target);
        if (target instanceof Slime slime && slime.getType() == EntityType.SLIME) {
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 4 * 20, 1));
            if (isBaby() && hunts(slime)) {
                growth = Math.max(1, growth - SLIME_GROWTH_TICKS);
            }
        }
        return true;
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        if (!isBaby() && !isBrooding() && level.getGameTime() >= broodReadyAt && entity instanceof Slime slime
                && slime.getType() == EntityType.SLIME && slime.getSize() == 4) {
            setBroodTicks(BROOD_TICKS);
        }
        return super.killedEntity(level, entity);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    void biteIfClose(LivingEntity target) {
        double range = getBbWidth() * .5 + target.getBbWidth() * .5 + .62;
        if (attackCooldown == 0 && distanceToSqr(target) < range * range && getSensing().hasLineOfSight(target)) {
            if (doHurtTarget(target)) {
                attackCooldown = 16;
            }
        }
    }

    boolean hasRider() {
        return getFirstPassenger() instanceof Player;
    }

    public boolean isTamed() {
        return entityData.get(OWNER).isPresent();
    }

    Player companionOwner() {
        return entityData.get(OWNER).map(level()::getPlayerByUUID).orElse(null);
    }

    boolean choosingLanding;

    public boolean isOwnedBy(Entity entity) {
        return entityData.get(OWNER).filter(entity.getUUID()::equals).isPresent();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && isOwnedBy(passenger) && !isBaby() && !isBrooding()
                && getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        choosingLanding = passenger instanceof Player && isTamed() && !onGround();
        sprintRequested = false;
        if (!level().isClientSide) {
            entityData.set(RIDE_MOTION, new Vector3f());
        }
        if (passenger instanceof ServerPlayer player) {
            // Vanilla no longer sends held jump input from a player once they dismount.
            player.setJumping(false);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() && canOpenBackpack(player)) {
            openCustomInventoryScreen(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.SUGAR_CANE) && (!isTamed() || isOwnedBy(player) && stamina() < STAMINA_MAX)) {
            if (!level().isClientSide) {
                LegacyItemData.consume(held, 1, player);
                restoreFromCane();
                if (!isTamed()) {
                    boolean success = random.nextFloat() < .20F;
                    if (success) {
                        entityData.set(OWNER, Optional.of(player.getUUID()));
                        setPersistenceRequired();
                        setTarget(null);
                        getNavigation().stop();
                    }
                    level().broadcastEntityEvent(this, success ? (byte) 7 : (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty() || player.isSecondaryUseActive() || !canAddPassenger(player)
                || getTarget() != null && getTarget().isAlive()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event != 6 && event != 7) {
            super.handleEntityEvent(event);
            return;
        }
        ParticleOptions particle = event == 7 ? ParticleTypes.HEART : ParticleTypes.SMOKE;
        for (int i = 0; i < 7; ++i) {
            level().addParticle(particle, getRandomX(1), getRandomY() + .4, getRandomZ(1),
                    random.nextGaussian() * .02, random.nextGaussian() * .02, random.nextGaussian() * .02);
        }
    }

    private boolean canOpenBackpack(Player player) {
        return isAlive() && player.isAlive() && !player.isSpectator() && isOwnedBy(player) && player.level() == level()
                && (player.getVehicle() == this || player.distanceToSqr(this) <= 64);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof ServerPlayer serverPlayer && canOpenBackpack(player)) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, viewer) -> ChestMenu.threeRows(id, inventory, backpack),
                    Component.translatable("container.yagens_mobs.velocipod_backpack")));
        }
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, backpack);
        }
    }

    public void setRiderSprint(ServerPlayer rider, boolean sprint) {
        if (getFirstPassenger() == rider && isOwnedBy(rider)) {
            sprintRequested = sprint;
            sprintInputAt = tickCount;
        }
    }

    private int stamina() {
        return entityData.get(STAMINA);
    }

    public float staminaPercent() {
        return stamina() / 100.0F;
    }

    public Quaternionf riderTilt(float partialTick) {
        float bank = Mth.lerp(partialTick, previousRideMotion.x, rideMotion.x);
        float pitch = riderPitch(partialTick);
        return new Quaternionf().rotationZ(bank * Mth.DEG_TO_RAD).rotateX(pitch * Mth.DEG_TO_RAD);
    }

    public float riderPitch(float partialTick) {
        return Mth.lerp(partialTick, previousRideMotion.y, rideMotion.y);
    }

    public float riderSpeedFraction(float partialTick) {
        return Mth.lerp(partialTick, previousRideMotion.z, rideMotion.z);
    }

    private boolean canSprintInFlight() {
        return stamina() >= STAMINA_TO_SPRINT;
    }

    private void adjustStamina(int change) {
        entityData.set(STAMINA, Mth.clamp(stamina() + change, 0, STAMINA_MAX));
    }

    void restoreFromCane() {
        adjustStamina(CANE_RESTORE);
        playSound(SoundEvents.GENERIC_EAT, .7F, 1.12F);
        gameEvent(GameEvent.EAT);
    }

    @Override
    public void tick() {
        Vec3 before = position();
        sprintingThisTick = false;
        super.tick();
        if (level().isClientSide) {
            previousRideMotion.set(rideMotion);
            if (hasRider()) {
                rideMotion.lerp(entityData.get(RIDE_MOTION), .32F);
            } else {
                previousRideMotion.zero();
                rideMotion.zero();
            }
            return;
        }
        if (!isAlive()) {
            return;
        }
        if (stamina() < CANE_RESTORE) {
            for (int i = 0; i < backpack.getContainerSize(); i++) {
                if (backpack.getItem(i).is(Items.SUGAR_CANE)) {
                    backpack.removeItem(i, 1);
                    restoreFromCane();
                    break;
                }
            }
        }

        // Charge for actual travel, including vertical movement. A blocked mount recovers like one hovering.
        double movementSquared = before.distanceToSqr(position());
        if (onGround()) {
            adjustStamina(100);
        } else if (movementSquared < IDLE_SPEED * IDLE_SPEED) {
            adjustStamina(20);
        } else if (movementSquared > IDLE_SPEED * IDLE_SPEED) {
            adjustStamina(sprintingThisTick ? -25 : -10);
        }
    }

    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 anchor = new Vec3(0, SEAT_HEIGHT * MODEL_SCALE / 16, SEAT_BACK * MODEL_SCALE / 16)
                .yRot((180 - getYRot()) * Mth.DEG_TO_RAD);
        move.accept(passenger, getX() + anchor.x, getY() + anchor.y, getZ() + anchor.z);
    }

    @Override
    protected void customServerAiStep() {
        updateAttackDamage();
        if (growth > 0 && --growth == 0) {
            setBaby(false);
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (hasRider()) {
            getNavigation().stop();
            setTarget(null);
        }
    }

    private void updateAttackDamage() {
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        double base = switch (level().getDifficulty()) {
            case EASY -> 8;
            case HARD -> 12;
            default -> 10;
        };
        if (damage.getBaseValue() != base) {
            damage.setBaseValue(base);
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (level().isClientSide || isNoAi()) {
            super.travel(input);
            return;
        }
        boolean wasGrounded = onGround();
        float previousYaw = getYRot();
        Player rider = getFirstPassenger() instanceof Player player ? player : null;
        boolean accelerate = canSprintInFlight() && (rider == null ? aiSprint
                : sprintRequested && tickCount - sprintInputAt <= SPRINT_INPUT_TIMEOUT);
        Vec3 wanted = rider == null ? aiMotion(wasGrounded) : pilotedMotion(rider, wasGrounded, accelerate);
        destination = Vec3.ZERO;
        aiSprint = false;
        sprintingThisTick = accelerate && wanted.lengthSqr() > IDLE_SPEED * IDLE_SPEED;
        if (isInWater()) {
            wanted = wanted.add(0, .16, 0);
        }
        var motion = getDeltaMovement().lerp(wanted, wasGrounded ? .62 : .35);
        if (wanted.y <= 0 && wasGrounded) {
            motion = new Vec3(motion.x, -.08, motion.z);
            double horizontal = motion.horizontalDistance();
            if (rider != null && horizontal > RIDDEN_GROUND_SPEED) {
                double fraction = RIDDEN_GROUND_SPEED / horizontal;
                motion = new Vec3(motion.x * fraction, motion.y, motion.z * fraction);
            }
        }
        if (rider != null) {
            motion = protectRider(rider, motion);
        }
        if (!level().hasChunkAt(BlockPos.containing(position().add(motion)))) {
            motion = Vec3.ZERO;
        }
        setDeltaMovement(motion);
        Vec3 before = position();
        move(MoverType.SELF, motion);
        Vec3 moved = position().subtract(before);
        float turn = Mth.wrapDegrees(getYRot() - previousYaw);
        if (rider != null) {
            updateRiderMotion(moved, turn);
        }
        setYBodyRot(getYRot());
        updateFlight(wasGrounded, wanted, turn, moved, rider != null);
        calculateEntityAnimation(false);
    }

    private Vec3 aiMotion(boolean grounded) {
        if (destination.lengthSqr() > 1.e-5) {
            faceMovement(destination, 16);
            return destination;
        }
        return new Vec3(0, grounded ? -.08 : -.21, 0);
    }

    private Vec3 pilotedMotion(Player rider, boolean grounded, boolean accelerate) {
        boolean jump = ((LivingEntityJumpInput) rider).yagensMobs$isJumping();
        setYRot(Mth.approachDegrees(getYRot(), rider.getYRot(), RIDDEN_TURN_STEP));
        // LivingEntity.aiStep damps the stored movement input before a server-side mount reads it.
        var input = new Vec3(Mth.clamp(rider.xxa / .98F, -1, 1), 0, Mth.clamp(rider.zza / .98F, -1, 1));
        if (input.horizontalDistanceSqr() > 1) {
            input = input.normalize();
        }
        Vec3 forward = Vec3.directionFromRotation(0, getYRot());
        Vec3 strafe = new Vec3(forward.z, 0, -forward.x);
        if (grounded && !jump) {
            return forward.scale(input.z).add(strafe.scale(input.x)).scale(RIDDEN_GROUND_SPEED).add(0, -.08, 0);
        }
        float pitch = Mth.clamp(rider.getXRot(), -90, 90) * Mth.DEG_TO_RAD;
        double speed = accelerate ? RIDDEN_SPRINT_SPEED : RIDDEN_FLIGHT_SPEED;
        var horizontal = forward.scale(input.z * Mth.cos(pitch)).add(strafe.scale(input.x));
        double vertical = jump ? 1 : -input.z * Mth.sin(pitch);
        Vec3 direction = new Vec3(horizontal.x, vertical, horizontal.z);
        return (direction.lengthSqr() > 1 ? direction.normalize() : direction).scale(speed);
    }

    private Vec3 protectRider(Player rider, Vec3 motion) {
        // The passenger stands above the vehicle's collision box. Use the swept destination
        // of both bodies so the mount cannot carry a rider through a low ceiling.
        var box = rider.getBoundingBox();
        if (level().noCollision(rider, box.expandTowards(motion))) {
            return motion;
        }
        var vertical = new Vec3(0, motion.y, 0);
        var horizontal = new Vec3(motion.x, 0, motion.z);
        if (motion.y != 0 && level().noCollision(rider, box.expandTowards(vertical))) {
            return vertical;
        }
        return level().noCollision(rider, box.expandTowards(horizontal)) ? horizontal : Vec3.ZERO;
    }

    private void faceMovement(Vec3 direction, float turn) {
        if (direction.horizontalDistanceSqr() > .0001) {
            float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
            setYRot(Mth.approachDegrees(getYRot(), yaw, turn));
        }
    }

    private void updateRiderMotion(Vec3 moved, float turn) {
        double horizontal = moved.horizontalDistance();
        float roll = 0;
        float pitch = 0;
        if (!onGround()) {
            float bankStrength = Mth.clamp((float) (horizontal / RIDDEN_SPRINT_SPEED), 0, 1);
            roll = -30 * bankStrength * Mth.clamp(turn / RIDDEN_TURN_STEP, -1, 1);
            if (Math.abs(moved.y) > .035) {
                pitch = Mth.clamp((float) Mth.atan2(moved.y, Math.max(.25, horizontal)) * Mth.RAD_TO_DEG * .65F, -14, 20);
            }
        }
        // Use displacement after collision so that a blocked mount neutralizes its tilt and camera distance.
        float speed = Mth.clamp((float) (moved.length() / RIDDEN_SPRINT_SPEED), 0, 1);
        entityData.set(RIDE_MOTION, new Vector3f(roll, pitch, speed));
    }

    private void updateFlight(boolean wasGrounded, Vec3 wanted, float turn, Vec3 moved, boolean ridden) {
        Flight phase = flight();
        flightTicks++;
        if (!wasGrounded && onGround()) {
            if (phase != Flight.LAND) {
                setFlight(Flight.LAND);
            }
            return;
        }
        if (onGround()) {
            if (ridden && moved.horizontalDistanceSqr() > .0025) {
                setFlight(Flight.WALK);
            } else if (phase != Flight.LAND || flightTicks >= 25) {
                setFlight(Flight.GROUND);
            }
            return;
        }
        if (wasGrounded || phase == Flight.GROUND || phase == Flight.WALK) {
            setFlight(Flight.TAKEOFF);
            return;
        }
        if (phase == Flight.TAKEOFF && flightTicks < 22) {
            return;
        }
        if (wanted.y < -.08 && closeToLanding()) {
            setFlight(Flight.LAND);
        } else if (ridden && sprintingThisTick && moved.lengthSqr() > IDLE_SPEED * IDLE_SPEED) {
            setFlight(Flight.SPRINT);
        } else if (wanted.horizontalDistanceSqr() < .018) {
            setFlight(Flight.HOVER);
        } else if (!ridden && Math.abs(turn) > 3) {
            setFlight(turn < 0 ? Flight.TURN_LEFT : Flight.TURN_RIGHT);
        } else if (ridden || (phase != Flight.TURN_LEFT && phase != Flight.TURN_RIGHT) || flightTicks >= 24) {
            setFlight(Flight.FLY);
        }
    }

    private boolean closeToLanding() {
        if (Double.isNaN(nextY) || (tickCount & 3) == 0) {
            var hit = level().clip(new ClipContext(position(), position().add(0, -2.2, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            nextY = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.NEGATIVE_INFINITY;
        }
        return getY() - nextY < 2.1;
    }

    private void setFlight(Flight phase) {
        if (flight() != phase) {
            entityData.set(FLIGHT, (byte) phase.ordinal());
            flightTicks = 0;
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", -growth);
        tag.putString("Variant", color().id());
        tag.putInt("BroodTicks", broodTicks());
        tag.putLong("BroodReadyAt", broodReadyAt);
        tag.putBoolean("CaneToPlant", replantCane);
        entityData.get(OWNER).ifPresent(owner -> tag.putUUID("Owner", owner));
        tag.putInt("Stamina", stamina());
        tag.put("Backpack", ContainerHelper.saveAllItems(new CompoundTag(), backpack.getItems()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setColor(Color.byId(tag.getString("Variant")));
        growth = -Mth.clamp(tag.getInt("Age"), -GROWTH_TICKS, 0);
        entityData.set(BABY, growth > 0);
        updateAgeAttributes();
        setHealth(Math.min(getHealth(), getMaxHealth()));
        setBroodTicks(isBaby() ? 0 : Mth.clamp(tag.getInt("BroodTicks"), 0, BROOD_TICKS));
        broodReadyAt = Math.max(0, tag.getLong("BroodReadyAt"));
        replantCane = tag.getBoolean("CaneToPlant");
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(STAMINA, tag.contains("Stamina", 99)
                ? Mth.clamp(tag.getInt("Stamina"), 0, STAMINA_MAX) : STAMINA_MAX);
        backpack.clearContent();
        var items = backpack.getItems();
        ContainerHelper.loadAllItems(tag.getCompound("Backpack"), items);
        backpack.applyItems(items);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 3, state -> state.setAndContinue(flight().animation)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animation;
    }

    public enum Flight {
        GROUND("ground", true),
        TAKEOFF("takeoff", false),
        HOVER("hover", true),
        FLY("fly", true),
        TURN_LEFT("turn_left", false),
        TURN_RIGHT("turn_right", false),
        LAND("land", false),
        WALK("walk", true),
        SPRINT("sprint", true);

        private final RawAnimation animation;

        Flight(String name, boolean repeat) {
            String id = "animation.velocipod." + name;
            animation = repeat ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlayAndHold(id);
        }
    }

    public enum Color {
        GREEN, PURPLE, BLUE, PINK, YELLOW;

        private static final Color[] VALUES = values();

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Color byId(String id) {
            for (var color : VALUES) {
                if (color.id().equals(id)) {
                    return color;
                }
            }
            return PURPLE;
        }

        public static Color random(RandomSource random) {
            return VALUES[random.nextInt(VALUES.length)];
        }
    }

    private static final class FlightControl extends MoveControl {
        private final Velocipod mob;

        FlightControl(Velocipod mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO || mob.hasRider()) {
                return;
            }
            operation = Operation.WAIT;
            var toward = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double distance = toward.length();
            if (distance < .13) {
                return;
            }
            mob.aiSprint = speedModifier > 1 && mob.canSprintInFlight();
            double limit = mob.aiSprint ? AI_SPRINT_SPEED : AI_FLIGHT_SPEED;
            double speed = Math.min(distance, Math.min(limit, speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED)));
            var step = toward.scale(speed / distance);
            if (mob.onGround() && distance > .6 && step.y > -.01) {
                step = new Vec3(step.x, Math.max(.15, step.y), step.z);
            }
            var directed = new Vec3(step.x, Mth.clamp(step.y, -.35, .40), step.z);
            mob.destination = directed.lengthSqr() > limit * limit ? directed.normalize().scale(limit) : directed;
        }
    }
}


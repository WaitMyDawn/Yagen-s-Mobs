package yagen.waitmydawn.yagens_mobs.entity.kavat;

import yagen.waitmydawn.yagens_mobs.entity.PetHazards;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
import com.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public class Kavat extends TamableAnimal implements GeoEntity {
    @Override
    public void move(MoverType kind, Vec3 motion) {
        if (kind == MoverType.SELF && level() instanceof ServerLevel
                && isTame() && !isPassenger() && !isVehicle()) {
            Vec3 safeMotion = PetHazards.steer(this, motion, false, false);
            if (safeMotion != motion) {
                motion = safeMotion;
                setDeltaMovement(safeMotion);
            }
        }
        super.move(kind, motion);
    }

    private static final EntityDataAccessor<Long> CHAMBER_START =
            SynchedEntityData.defineId(Kavat.class, EntityDataSerializers.LONG);
    static final int CHAMBER_EXIT_TICKS = 154;

    void beginChamberExit(long time) {
        entityData.set(CHAMBER_START, time);
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        setTarget(null);
        getNavigation().stop();
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    float chamberAge(float partial) {
        long start = entityData.get(CHAMBER_START);
        return start < 0 ? CHAMBER_EXIT_TICKS : Mth.clamp(level().getGameTime() - start + partial, 0, CHAMBER_EXIT_TICKS);
    }

    private UUID ownerUuid() { return getOwnerReference() == null ? null : getOwnerReference().getUUID(); }

    public boolean emerging() { return chamberAge(0) < CHAMBER_EXIT_TICKS; }

    @Override public boolean isEffectiveAi() { return super.isEffectiveAi() && !emerging(); }
    @Override public boolean isPushable() { return super.isPushable() && !emerging(); }
    private static final EntityDataAccessor<Integer> TRAITS =
            SynchedEntityData.defineId(Kavat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIDDEN =
            SynchedEntityData.defineId(Kavat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> ACTIVITY =
            SynchedEntityData.defineId(Kavat.class, EntityDataSerializers.BYTE);
    void pose(KavatHabits.Pose pose) { entityData.set(ACTIVITY, (byte) pose.ordinal()); }
    KavatHabits.Pose activity() { return KavatHabits.Pose.VALUES[entityData.get(ACTIVITY)]; }
    boolean closerThanSpot(BlockPos pos) {
        return position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)) < .65;
    }
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int generation, slices, cooldown;
    private boolean extracted;
    private boolean pendingFortressCoat;
    private UUID image, origin;
    final KavatTurn turnMotion;
    private float horizontalSpeed;
    private static final EntityDataAccessor<Boolean> POUNCING =
            SynchedEntityData.defineId(Kavat.class, EntityDataSerializers.BOOLEAN);
    private boolean runningGait;
    boolean pouncing() { return entityData.get(POUNCING); }
    void setPouncing(boolean value) { entityData.set(POUNCING, value); }
    @Override
    public boolean shouldTryTeleportToOwner() {
        var owner = getOwner();
        return owner != null && distanceToSqr(owner) > 64 * 64;
    }
    private float fanRise, oldFanRise, fanSweep, oldFanSweep;
    boolean hasFanTail() {
        return KavatGenes.TAILS.get(genes().tail()).equals("fan");
    }
    float fanRise(float partial) { return Mth.lerp(partial, oldFanRise, fanRise); }
    float fanSweep(float partial) { return Mth.lerp(partial, oldFanSweep, fanSweep); }

    @Override
    public void tick() {
        yagen.waitmydawn.yagens_mobs.entity.CompanionTravel.moved(this);
        if (pendingFortressCoat && level() instanceof ServerLevel server) {
            pendingFortressCoat = false;
            if (server.dimension() == Level.NETHER && KavatSpawns.isWitherSpawnSite(server, blockPosition())) {
                var current = genes();
                phenotype(new KavatGenes(current.breed(), 8, current.ear(), current.tail()), generation);
            }
        }
        super.tick();
        if (!level().isClientSide()) return;
        double dx=getX()-xo, dy=getY()-yo, dz=getZ()-zo;
        boolean reset = tickCount < 2 || dx*dx + dy*dy + dz*dz > 4;
        boolean pivot = onGround() && dx*dx + dz*dz < .0036 && !isInSittingPose()
                && activity() == KavatHabits.Pose.NONE && tickCount >= riseUntil && isAlive();
        turnMotion.tick(yBodyRot, pivot, reset);
        updateGait(dx, dz, reset);
        if (!hasFanTail()) return;
        float visualYaw = turnMotion.heading(1, yBodyRot);
        float turn = visualYaw - turnMotion.heading(0, yBodyRot);
        oldFanRise=fanRise;
        oldFanSweep=fanSweep;
        if (reset) {
            oldFanRise=fanRise=oldFanSweep=fanSweep=0;
            return;
        }
        double heading=Math.toRadians(visualYaw);
        double lateral=dx*Math.cos(heading)+dz*Math.sin(heading);
        fanRise=Mth.lerp(.31F,fanRise,Mth.clamp((float)dy*3,-.45F,1F));
        fanSweep=Mth.lerp(.26F,fanSweep,Mth.clamp((float)lateral*4+turn/23F,-1F,1F));
    }
    private void updateGait(double dx, double dz, boolean reset) {
        boolean grounded = onGround();
        if (reset || isPassenger()) {
            horizontalSpeed = 0;
            runningGait = false;
        } else {
            // walkAnimation.speed is a capped limb-swing amplitude. Gait thresholds use blocks per second.
            float measured = (float) Math.hypot(dx, dz) * 20;
            horizontalSpeed = Mth.lerp(.36F, horizontalSpeed, measured);
            if (grounded) {
                runningGait = horizontalSpeed >= (runningGait ? 4.35F : 5.25F);
            }
        }
    }

    public Kavat(EntityType<? extends Kavat> type, Level level) {
        super(type, level);
        turnMotion = level.isClientSide() ? new KavatTurn() : null;
    }
    static final net.minecraft.world.entity.animal.feline.CatSoundVariant.CatSoundSet SOUNDS =
            SoundEvents.CAT_SOUNDS.get(net.minecraft.world.entity.animal.feline.CatSoundVariants.SoundSet.CLASSIC).adultSounds();

    @Override
    protected SoundEvent getAmbientSound() {
        if (decoy() || hidden()) return null;
        return activity() == KavatHabits.Pose.WARM || activity() == KavatHabits.Pose.SOCIAL_ROLL
                || isInSittingPose() ? SOUNDS.purrSound().value() : SOUNDS.ambientSound().value();
    }
    @Override
    protected SoundEvent getHurtSound(DamageSource damage) { return SOUNDS.hurtSound().value(); }
    @Override
    protected SoundEvent getDeathSound() { return SOUNDS.deathSound().value(); }
    public boolean decoy() {
        return getType() == ModEntities.KAVAT_DECOY.get();
    }
    public boolean hidden() {
        return entityData.get(HIDDEN);
    }
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        // Placement checks the floor. Animal's walk-target score would reject dark non-grass floors.
        if (reason == EntitySpawnReason.NATURAL && level instanceof ServerLevel server
                && server.dimension() == Level.END && server.getBiome(blockPosition()).is(net.minecraft.world.level.biome.Biomes.THE_END)) {
            int spacing = ServerConfigs.KAVAT_MAIN_ISLAND_SPACING.get();
            return spacing == 0 || server.getEntitiesOfClass(Kavat.class, getBoundingBox().inflate(spacing),
                    other -> !other.isTame() && !other.decoy() && other.distanceToSqr(this) < (double) spacing * spacing).isEmpty();
        }
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION || super.checkSpawnRules(level, reason);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return ServerConfigs.KAVAT.maxGroupCount();
    }
    private int cachedBits = -1;
    private KavatGenes phenotype;
    public KavatGenes genes() {
        int bits = entityData.get(TRAITS);
        if (bits != cachedBits) {
            phenotype = KavatGenes.unpack(bits);
            cachedBits = bits;
        }
        return phenotype;
    }
    public KavatSample sample() {
        return new KavatSample(genes(), generation, ownerUuid());
    }
    public int remaining() {
        return 2 - slices;
    }
    ItemStack slice() {
        if (decoy() || !isTame() || slices >= 2)
            return ItemStack.EMPTY;
        slices++;
        return sample().stack(true, 1);
    }
    void phenotype(KavatGenes genes, int generation) {
        entityData.set(TRAITS, genes.packed());
        this.generation = generation;
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(genes.breed() == 2 ? 10 : 8);
    }
    @Override
    protected float sanitizeScale(float scale) {
        // Keep saved attribute values and command-applied scale modifiers relative to the species size.
        return super.sanitizeScale(scale) * 1.5F;
    }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, .31)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.FOLLOW_RANGE, 32);
    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CHAMBER_START, -1L);
        b.define(TRAITS, 0);
        b.define(HIDDEN, false);
        b.define(POUNCING, false);
        b.define(ACTIVITY, (byte) 0);
    }
    @Override
    protected void registerGoals() {
        if (decoy()) {
            goalSelector.addGoal(0, new FloatGoal(this));
        } else {
            goalSelector.addGoal(0, new KavatHabits.FireRoll(this));
            goalSelector.addGoal(0, new KavatHabits.Water(this));
            goalSelector.addGoal(1, new IncubatorExitGoal(this));
            goalSelector.addGoal(4, new KavatHabits.Leisure(this));
        }
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        if (!decoy()) {
            goalSelector.addGoal(2, new KavatMeleeGoal(this));
            goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.15, 6, 2));
            targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
            targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
            targetSelector.addGoal(3, new HurtByTargetGoal(this));
        }
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .55));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
    }
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData group) {
        var g = KavatGenes.random(random::nextInt);
        int coat = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
                ? KavatSpawns.coat(level, blockPosition(), random)
                : g.coat();
        phenotype(new KavatGenes(g.breed(), coat, g.ear(), g.tail()), 0);
        // Structure references may point beyond a population worker's chunk window.
        pendingFortressCoat = reason == EntitySpawnReason.CHUNK_GENERATION && !(level instanceof ServerLevel)
                && level.getLevel().dimension() == Level.NETHER;
        return super.finalizeSpawn(level, difficulty, reason, new AgeableMobGroupData(false));
    }
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }
    @Override
    public boolean isFood(ItemStack food) {
        return false;
    }
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (decoy())
            return InteractionResult.PASS;
        var held = player.getItemInHand(hand);
        if (held.is(ModItems.KAVAT_TEMPLATE.get()) && !held.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            if (!isOwnedBy(player)) return InteractionResult.PASS;
            if (!level().isClientSide()) {
                ItemStack imprint = slice();
                if (imprint.isEmpty()) {
                    player.sendOverlayMessage(Component.translatable("kavat.no_slices"));
                } else {
                    held.consume(1, player);
                    if (held.isEmpty()) player.setItemInHand(hand, imprint);
                    else give(player, imprint);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModItems.KAVAT_EXTRACTOR.get())) {
            if (!level().isClientSide()) {
                if (extracted)
                    player.sendOverlayMessage(Component.translatable("kavat.extracted"));
                else {
                    extracted = true;
                    held.consume(1, player);
                    int count = 1 + random.nextInt(3);
                    var sample = sample();
                    give(player, sample.stack(false, count));
                    player.sendOverlayMessage(Component.translatable("kavat.sample_received", sample.description(), count));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && held.isEmpty() && player.isShiftKeyDown() && isOwnedBy(player)) {
            if (!level().isClientSide()) {
                setOrderedToSit(!isOrderedToSit());
                setTarget(null);
                getNavigation().stop();
                player.sendOverlayMessage(Component.translatable(
                        isOrderedToSit() ? "kavat.mode.stay" : "kavat.mode.follow", getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack))
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || emerging())
            return;
        if (decoy()) {
            if (origin == null || !(server.getEntity(origin) instanceof Kavat cat) || !cat.isAlive()
                    || !getUUID().equals(cat.image))
                discard();
            return;
        }
        if (cooldown > 0)
            cooldown--;
        if (hidden()) {
            if (getTarget() == null || !getTarget().isAlive() || image == null
                    || !(server.getEntity(image) instanceof Kavat copy) || !copy.isAlive()) {
                reveal(server);
            } else if (tickCount % 5 == 0)
                lure(server, copy);
        } else if (genes().breed() == 0 && cooldown == 0 && getTarget() != null && getTarget().isAlive()) {
            Kavat copy = ModEntities.KAVAT_DECOY.get().create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (copy != null) {
                copy.snapTo(position(), getYRot(), getXRot());
                copy.phenotype(genes(), 0);
                copy.origin = getUUID();
                if (server.addFreshEntity(copy)) {
                    image = copy.getUUID();
                    entityData.set(HIDDEN, true);
                    lure(server, copy);
                }
            }
        }
    }
    Kavat visibleDecoy(ServerLevel level) {
        return image != null && level.getEntity(image) instanceof Kavat k && k.isAlive() ? k : null;
    }
    private void lure(ServerLevel server, Kavat copy) {
        for (Mob mob : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32),
                     m -> m != copy && m != this && (m.getTarget() == this || m == getTarget() && !isAlliedTo(m))))
            mob.setTarget(copy);
    }
    private void reveal(ServerLevel level) {
        entityData.set(HIDDEN, false);
        cooldown = 360;
        if (image != null && level.getEntity(image) instanceof Kavat k && k.decoy())
            k.discard();
        image = null;
    }
    @Override
    public boolean isInvisible() {
        return hidden() || super.isInvisible();
    }
    @Override
    public boolean isInvisibleTo(Player viewer) {
        return hidden() || super.isInvisibleTo(viewer);
    }
    @Override
    public boolean isCurrentlyGlowing() {
        return hidden() && level().isClientSide() && KavatView.localOwner(this) || super.isCurrentlyGlowing();
    }
    @Override
    public int getTeamColor() {
        return hidden() ? KavatSample.COLORS[genes().coat()] : super.getTeamColor();
    }
    @Override
    public void die(DamageSource source) {
        CompoundTag record = null;
        if (!dead && !decoy() && isTame() && ownerUuid() != null && level() instanceof ServerLevel) {
            var output = SaveTag.output(registryAccess());
            saveWithoutId(output);
            record = output.buildResult();
        }
        super.die(source);
        // Death can be cancelled by another listener. Archive only a committed death.
        if (dead && record != null && level() instanceof ServerLevel server) {
            if (hidden())
                reveal(server);
            KavatMemories.store(server, ownerUuid(), getUUID(), record);
        }
    }
    @Override
    public boolean shouldBeSaved() {
        // A frozen death animation must not reintroduce the companion UUID after revival.
        return !(dead && isTame()) && super.shouldBeSaved();
    }
    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return !decoy() && !isTame() && super.shouldDropLoot(level);
    }
    @Override
    public boolean shouldDropExperience() {
        return !decoy() && super.shouldDropExperience();
    }
    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput n) {
        super.addAdditionalSaveData(n);
        if (emerging()) n.putLong("ChamberStart", entityData.get(CHAMBER_START));
        n.putInt("KavatTraits", genes().packed());
        n.putInt("Generation", generation);
        n.putInt("TemplatesMade", slices);
        n.putBoolean("Extracted", extracted);
        if (pendingFortressCoat) n.putBoolean("PendingFortressCoat", true);
        n.putInt("DecoyCooldown", hidden() ? 360 : cooldown);
    }
    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput n) {
        super.readAdditionalSaveData(n);
        entityData.set(CHAMBER_START, n.getLongOr("ChamberStart", -1L));
        phenotype(KavatGenes.unpack(n.getIntOr("KavatTraits", 0)), Math.clamp(n.getIntOr("Generation", 0), 0, 100));
        slices = Math.clamp(n.getIntOr("TemplatesMade", 0), 0, 2);
        extracted = n.getBooleanOr("Extracted", false);
        pendingFortressCoat = n.getBooleanOr("PendingFortressCoat", false);
        cooldown = n.getIntOr("DecoyCooldown", 0);
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
    private static RawAnimation loop(String clip) {
        return RawAnimation.begin().thenLoop("animation.kavat." + clip);
    }
    private static RawAnimation once(String clip) {
        return RawAnimation.begin().thenPlay("animation.kavat." + clip);
    }
    private static final RawAnimation HOP = RawAnimation.begin().thenPlayAndHold("animation.kavat.hop");
    private static final RawAnimation CHAMBER_EXIT = RawAnimation.begin().thenPlayAndHold("animation.kavat.chamber_exit");
    private static final RawAnimation IDLE = loop("idle"), RUN = loop("run"), WALK = loop("stalk"), JUMP = once("jump"),
                                      LISTEN = once("listen"), TAIL = once("tail_lift"), STRETCH = once("stretch"),
                                      FLAT = RawAnimation.begin().thenPlayAndHold("animation.kavat.flat_down"), FLAT_REST = loop("flat_rest"),
                                      CURLED = RawAnimation.begin().thenPlayAndHold("animation.kavat.curled_down"), CURLED_REST = loop("curled_rest"),
                                      FLAT_UP = once("flat_up"), CURLED_UP = once("curled_up");
    private static final RawAnimation SIDE = RawAnimation.begin().thenPlayAndHold("animation.kavat.side_down"), SIDE_REST = loop("side_rest"),
            SIDE_UP = once("side_up"), ROLL = once("social_roll"),
            FIRE = RawAnimation.begin().thenPlayAndHold("animation.kavat.fire_roll"),
            FLIP = RawAnimation.begin().thenPlayAndHold("animation.kavat.backflip"), PADDLE = loop("paddle"), SWIM = loop("swim");
    private KavatHabits.Pose previous = KavatHabits.Pose.NONE;
    private RawAnimation rise;
    private boolean resting;
    private int riseUntil;
    private RawAnimation restEntry;
    private int restBegan;

    private RawAnimation motion() {
        RawAnimation next = requestedMotion();
        if (next != CURLED && next != FLAT && next != SIDE) {
            restEntry = null;
            return next;
        }
        if (restEntry != next) {
            restEntry = next;
            restBegan = tickCount;
        }
        // Hold the authored end pose through the four blend ticks before handing off to breathing.
        int elapsed = tickCount - restBegan;
        if (next == CURLED && elapsed >= 48) return CURLED_REST;
        if (next == FLAT && elapsed >= 34) return FLAT_REST;
        if (next == SIDE && elapsed >= 31) return SIDE_REST;
        return next;
    }

    private RawAnimation requestedMotion() {
        boolean curl = (getUUID().getLeastSignificantBits() & 1) == 0;
        var active = activity();
        if (active != previous) {
            if (previous == KavatHabits.Pose.SIDE || previous == KavatHabits.Pose.WARM) {
                rise = previous == KavatHabits.Pose.SIDE ? SIDE_UP : CURLED_UP;
                riseUntil = tickCount + (previous == KavatHabits.Pose.SIDE ? 25 : 39);
            }
            previous = active;
        }
        RawAnimation behavior = switch (active) {
            case WARM -> CURLED;
            case SIDE -> SIDE;
            case FIRE_ROLL -> FIRE;
            case BACKFLIP -> FLIP;
            case SOCIAL_ROLL -> ROLL;
            case PADDLE -> PADDLE;
            case SWIM -> SWIM;
            default -> null;
        };
        if (behavior != null) return behavior;
        if (isInSittingPose()) {
            resting = true;
            return curl ? CURLED : FLAT;
        }
        if (resting) {
            resting = false;
            rise = curl ? CURLED_UP : FLAT_UP;
            riseUntil = tickCount + (curl ? 39 : 26);
        }
        if (!onGround() && !isInWater() && level().noCollision(this, getBoundingBox().move(0, -.08, 0)))
            return pouncing() ? JUMP : HOP;
        if (horizontalSpeed > .20F)
            return runningGait ? RUN : WALK;
        if (tickCount < riseUntil)
            return rise;
        int phase = Math.floorMod(tickCount + getId() * 41, 1000);
        if (phase < 58)
            return TAIL;
        if (phase >= 340 && phase < 428)
            return STRETCH;
        if (phase >= 720 && phase < 756)
            return LISTEN;
        return IDLE;
    }
    // At unit scale the planted paws travel 7.8 model pixels during 29% of the 0.64-second gallop.
    double gallopRate() {
        return horizontalSpeed * (.29 * .64 * 16 / 7.8) / getScale();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<Kavat>("body", 4, state -> {
            float age = chamberAge(state.getDataOrDefault(com.geckolib.constant.DataTickets.PARTIAL_TICK, 0F));
            var next = age < CHAMBER_EXIT_TICKS ? CHAMBER_EXIT : motion();
            var controller = state.controller();
            var previous = controller.getCurrentRawAnimation();
            controller.setTransitionTicks(next == FIRE || next == FLIP || previous == FIRE || previous == FLIP || next == ROLL || next == CHAMBER_EXIT || next == CURLED_REST || next == FLAT_REST || next == SIDE_REST ? 0 : 4);
            controller.setAnimationSpeed(next == RUN ? gallopRate() : next == WALK ? 1.45 : 1.0);
            var result = state.setAndContinue(next);
            if (next == CHAMBER_EXIT && controller.getTimeline() != null) controller.setAnimationTime(age / 20.0);
            return result;
        }));
    }
}

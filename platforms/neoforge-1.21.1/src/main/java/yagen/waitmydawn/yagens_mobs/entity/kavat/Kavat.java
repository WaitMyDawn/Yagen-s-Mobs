package yagen.waitmydawn.yagens_mobs.entity.kavat;

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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public class Kavat extends TamableAnimal implements GeoEntity {
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
    private float fanRise, oldFanRise, fanSweep, oldFanSweep, fanHeading;
    boolean hasFanTail() {
        return KavatGenes.TAILS.get(genes().tail()).equals("fan");
    }
    float fanRise(float partial) { return Mth.lerp(partial, oldFanRise, fanRise); }
    float fanSweep(float partial) { return Mth.lerp(partial, oldFanSweep, fanSweep); }

    @Override
    public void tick() {
        if (pendingFortressCoat && level() instanceof ServerLevel server) {
            pendingFortressCoat = false;
            if (server.dimension() == Level.NETHER && KavatSpawns.isWitherSpawnSite(server, blockPosition())) {
                var current = genes();
                phenotype(new KavatGenes(current.breed(), 8, current.ear(), current.tail()), generation);
            }
        }
        super.tick();
        if (!level().isClientSide || !hasFanTail()) return;
        double dx=getX()-xo, dy=getY()-yo, dz=getZ()-zo;
        float turn=Mth.wrapDegrees(yBodyRot-fanHeading);
        fanHeading=yBodyRot;
        oldFanRise=fanRise;
        oldFanSweep=fanSweep;
        if (tickCount<2 || dx*dx+dy*dy+dz*dz>4) {
            oldFanRise=fanRise=oldFanSweep=fanSweep=0;
            return;
        }
        double heading=Math.toRadians(yBodyRot);
        double lateral=dx*Math.cos(heading)+dz*Math.sin(heading);
        fanRise=Mth.lerp(.31F,fanRise,Mth.clamp((float)dy*3,-.45F,1F));
        fanSweep=Mth.lerp(.26F,fanSweep,Mth.clamp((float)lateral*4+turn/23F,-1F,1F));
    }
    public Kavat(EntityType<? extends Kavat> type, Level level) {
        super(type, level);
    }
    @Override
    protected SoundEvent getAmbientSound() {
        if (decoy() || hidden()) return null;
        return activity() == KavatHabits.Pose.WARM || activity() == KavatHabits.Pose.SOCIAL_ROLL
                || isInSittingPose() ? SoundEvents.CAT_PURR : SoundEvents.CAT_AMBIENT;
    }
    @Override
    protected SoundEvent getHurtSound(DamageSource damage) { return SoundEvents.CAT_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.CAT_DEATH; }
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
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        // Placement checks the floor. Animal's walk-target score would reject dark non-grass floors.
        if (reason == MobSpawnType.NATURAL && level instanceof ServerLevel server
                && server.dimension() == Level.END && server.getBiome(blockPosition()).is(net.minecraft.world.level.biome.Biomes.THE_END)) {
            int spacing = ServerConfigs.KAVAT_MAIN_ISLAND_SPACING.get();
            return spacing == 0 || server.getEntitiesOfClass(Kavat.class, getBoundingBox().inflate(spacing),
                    other -> !other.isTame() && !other.decoy() && other.distanceToSqr(this) < (double) spacing * spacing).isEmpty();
        }
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION || super.checkSpawnRules(level, reason);
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
        return new KavatSample(genes(), generation, getOwnerUUID());
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
    public float getScale() {
        // Keep saved attribute values and command-applied scale modifiers relative to the species size.
        return super.getScale() * 1.5F;
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
        b.define(TRAITS, 0);
        b.define(HIDDEN, false);
        b.define(ACTIVITY, (byte) 0);
    }
    @Override
    protected void registerGoals() {
        if (decoy()) {
            goalSelector.addGoal(0, new FloatGoal(this));
        } else {
            goalSelector.addGoal(0, new KavatHabits.FireRoll(this));
            goalSelector.addGoal(0, new KavatHabits.Water(this));
            goalSelector.addGoal(4, new KavatHabits.Leisure(this));
        }
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        if (!decoy()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
            goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.15, 6, 2));
            targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
            targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
            targetSelector.addGoal(3, new HurtByTargetGoal(this));
        }
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .85));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
    }
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData group) {
        var g = KavatGenes.random(random::nextInt);
        int coat = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                ? KavatSpawns.coat(level, blockPosition(), random)
                : g.coat();
        phenotype(new KavatGenes(g.breed(), coat, g.ear(), g.tail()), 0);
        // Structure references may point beyond a population worker's chunk window.
        pendingFortressCoat = reason == MobSpawnType.CHUNK_GENERATION && !(level instanceof ServerLevel)
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
        if (held.is(ModItems.KAVAT_EXTRACTOR.get())) {
            if (!level().isClientSide) {
                if (extracted)
                    player.displayClientMessage(Component.translatable("kavat.extracted"), true);
                else {
                    extracted = true;
                    held.consume(1, player);
                    int count = 1 + random.nextInt(3);
                    var sample = sample();
                    give(player, sample.stack(false, count));
                    player.displayClientMessage(Component.translatable("kavat.sample_received", sample.description(), count), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.isEmpty() && isOwnedBy(player)) {
            if (!level().isClientSide) {
                setOrderedToSit(!isOrderedToSit());
                setTarget(null);
                getNavigation().stop();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
    }
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server))
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
            Kavat copy = ModEntities.KAVAT_DECOY.get().create(server);
            if (copy != null) {
                copy.moveTo(position(), getYRot(), getXRot());
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
        return hidden() && level().isClientSide && KavatView.localOwner(this) || super.isCurrentlyGlowing();
    }
    @Override
    public int getTeamColor() {
        return hidden() ? KavatSample.COLORS[genes().coat()] : super.getTeamColor();
    }
    @Override
    public void die(DamageSource source) {
        CompoundTag record = null;
        if (!dead && !decoy() && isTame() && getOwnerUUID() != null && level() instanceof ServerLevel) {
            record = new CompoundTag();
            saveWithoutId(record);
        }
        super.die(source);
        // Death can be cancelled by another listener. Archive only a committed death.
        if (dead && record != null && level() instanceof ServerLevel server) {
            if (hidden())
                reveal(server);
            KavatMemories.store(server, getOwnerUUID(), getUUID(), record);
        }
    }
    @Override
    public boolean shouldBeSaved() {
        // A frozen death animation must not reintroduce the companion UUID after revival.
        return !(dead && isTame()) && super.shouldBeSaved();
    }
    @Override
    protected boolean shouldDropLoot() {
        return !decoy() && !isTame() && super.shouldDropLoot();
    }
    @Override
    public boolean shouldDropExperience() {
        return !decoy() && super.shouldDropExperience();
    }
    @Override
    public void addAdditionalSaveData(CompoundTag n) {
        super.addAdditionalSaveData(n);
        n.putInt("KavatTraits", genes().packed());
        n.putInt("Generation", generation);
        n.putInt("TemplatesMade", slices);
        n.putBoolean("Extracted", extracted);
        if (pendingFortressCoat) n.putBoolean("PendingFortressCoat", true);
        n.putInt("DecoyCooldown", hidden() ? 360 : cooldown);
    }
    @Override
    public void readAdditionalSaveData(CompoundTag n) {
        super.readAdditionalSaveData(n);
        phenotype(KavatGenes.unpack(n.getInt("KavatTraits")), Math.clamp(n.getInt("Generation"), 0, 100));
        slices = Math.clamp(n.getInt("TemplatesMade"), 0, 2);
        extracted = n.getBoolean("Extracted");
        pendingFortressCoat = n.getBoolean("PendingFortressCoat");
        cooldown = n.getInt("DecoyCooldown");
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
    private static final RawAnimation IDLE = loop("idle"), RUN = loop("run"), WALK = loop("stalk"), JUMP = once("jump"),
                                      LISTEN = once("listen"), TAIL = once("tail_lift"), STRETCH = once("stretch"),
                                      FLAT = once("flat_down").thenLoop("animation.kavat.flat_rest"),
                                      CURLED = once("curled_down").thenLoop("animation.kavat.curled_rest"),
                                      FLAT_UP = once("flat_up"), CURLED_UP = once("curled_up");
    private static final RawAnimation SIDE = once("side_down").thenLoop("animation.kavat.side_rest"),
            SIDE_UP = once("side_up"), ROLL = once("social_roll"),
            FIRE = RawAnimation.begin().thenPlayAndHold("animation.kavat.fire_roll"),
            FLIP = RawAnimation.begin().thenPlayAndHold("animation.kavat.backflip"), PADDLE = loop("paddle"), SWIM = loop("swim");
    private KavatHabits.Pose previous = KavatHabits.Pose.NONE;
    private RawAnimation rise;
    private boolean resting;
    private int riseUntil;
    private RawAnimation motion(boolean moving) {
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
            return JUMP;
        if (moving)
            return walkAnimation.speed() > .40F ? RUN : WALK;
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
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "body", 4, state -> {
            RawAnimation next = motion(state.isMoving());
            var controller = state.getController();
            RawAnimation current = controller.getCurrentRawAnimation();
            if (current != next) {
                // Blending a completed 360-degree Euler track back to zero unwinds the entire turn.
                boolean fullTurn = current == FIRE || current == FLIP || next == FIRE || next == FLIP;
                controller.transitionLength(fullTurn || next == ROLL ? 0 : 4);
            }
            return state.setAndContinue(next);
        }));
    }
}

package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import net.minecraft.sounds.SoundEvent;
import yagen.waitmydawn.yagens_mobs.entity.PetHazards;
import net.minecraft.world.entity.MoverType;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin.LumenOrbAccess;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;

public final class LumenButterfly extends TamableAnimal implements GeoEntity {
    @Override
    public void move(MoverType kind, Vec3 motion) {
        if (kind == MoverType.SELF && level() instanceof ServerLevel
                && isTame() && !isPassenger() && !isVehicle()) {
            Vec3 safeMotion = PetHazards.steer(this, motion, !isBaby(), true);
            if (safeMotion != motion) {
                motion = safeMotion;
                setDeltaMovement(safeMotion);
            }
        }
        super.move(kind, motion);
    }

    private static final EntityDataAccessor<String> COLOR =
            SynchedEntityData.defineId(LumenButterfly.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LINEAGE =
            SynchedEntityData.defineId(LumenButterfly.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_XP =
            SynchedEntityData.defineId(LumenButterfly.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WATER_BUBBLE =
            SynchedEntityData.defineId(LumenButterfly.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WAITING =
            SynchedEntityData.defineId(LumenButterfly.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("animation.lumen_butterfly_adult.flight");
    private static final RawAnimation PERCHED = RawAnimation.begin().thenLoop("animation.lumen_butterfly_adult.perched");
    private static final RawAnimation LARVA_REST = RawAnimation.begin().thenLoop("animation.lumen_butterfly_juvenile.rest");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.lumen_butterfly_juvenile.crawl");
    private static final int BREED_RADIUS = 8;
    private static final int BREED_WINDOW = 1200;
    private static final int BREED_COOLDOWN = 6000;
    private static final int TOTEM_COOLDOWN = 12000;
    private static final double PRECISION = 1.0e-8;
    private static final double MIN_RAINBOW_XP = -1000;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double storedXp;
    private boolean female;
    private long breedReadyUntil;
    private long breedCooldownUntil;
    private long totemCooldownUntil;
    private LumenButterfly mate;

    public LumenButterfly(EntityType<? extends LumenButterfly> type, Level level) {
        super(type, level);
        configureMovement();
    }

    static AttributeSupplier.Builder attributes() {
        return Animal.createLivingAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.FLYING_SPEED, .25)
                .add(Attributes.MOVEMENT_SPEED, .15).add(Attributes.FOLLOW_RANGE, 28);
    }

    static boolean checkSpawnPlacement(EntityType<LumenButterfly> type, ServerLevelAccessor level, MobSpawnType reason,
                                       BlockPos pos, RandomSource random) {
        return ServerConfigs.LUMEN_BUTTERFLY.canSpawn() && Animal.checkAnimalSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData group, CompoundTag spawnTag) {
        setLineage(LumenColor.random(random), 0);
        female = random.nextBoolean();
        // Natural groups contain flying adults. The larval stage starts when parents breed.
        return super.finalizeSpawn(level, difficulty, reason, new AgeableMob.AgeableMobGroupData(false), spawnTag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(COLOR, LumenColor.GOLD.id());
        entityData.define(LINEAGE, LumenColor.GOLD.lineageBit());
        entityData.define(HAS_XP, false);
        entityData.define(WATER_BUBBLE, false);
        entityData.define(WAITING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new LumenFlightGoal(this));
        goalSelector.addGoal(1, new Goal() {
            private int repath;

            {
                setFlags(EnumSet.of(Flag.MOVE));
            }

            @Override
            public boolean canUse() {
                return isBaby() && !isOrderedToSit() && getOwner() instanceof ServerPlayer owner && owner.isAlive()
                        && !owner.isSpectator() && distanceToSqr(owner) > 9;
            }

            @Override
            public void tick() {
                if (--repath <= 0 && getOwner() instanceof ServerPlayer owner) {
                    repath = adjustedTickDelay(12);
                    getNavigation().moveTo(owner, 1.2);
                }
            }

            @Override
            public void stop() {
                repath = 0;
                getNavigation().stop();
            }
        });
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return isBaby() && !isOrderedToSit() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isBaby() && !isOrderedToSit() && super.canContinueToUse();
            }
        });
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isBaby() ? null : CreatureCue.BUTTERFLY_IDLE.orElse(null);
    }

    private void configureMovement() {
        if (isBaby()) {
            navigation = new GroundPathNavigation(this, level());
            moveControl = new MoveControl(this);
            setNoGravity(false);
        } else {
            navigation = createNavigation(level());
            moveControl = new FlyingMoveControl(this, 25, true);
            setNoGravity(true);
        }
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        configureMovement();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isBaby() ? EntityDimensions.scalable(.19F, .095F) : super.getDimensions(pose);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    public LumenColor color() {
        return LumenColor.byId(entityData.get(COLOR));
    }

    int lineage() {
        return entityData.get(LINEAGE);
    }

    public boolean isRainbow() {
        return lineage() == LumenColor.FULL_LINEAGE;
    }

    boolean hasPower(LumenColor color) {
        return !isRainbow() && color() == color;
    }

    private void setLineage(LumenColor color, int inherited) {
        entityData.set(COLOR, color.id());
        entityData.set(LINEAGE, (inherited & LumenColor.FULL_LINEAGE) | color.lineageBit());
    }

    public boolean hasStoredXp() {
        return entityData.get(HAS_XP);
    }

    public boolean inBubble() {
        return entityData.get(WATER_BUBBLE);
    }

    void setBubble(boolean active) {
        entityData.set(WATER_BUBBLE, active);
    }

    @Override
    protected int decreaseAirSupply(int currentAir) {
        return inBubble() ? currentAir : super.decreaseAirSupply(currentAir);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player attacker && attacker.getUUID().equals(getOwnerUUID())
                && level() instanceof ServerLevel level && followSlot(level) >= 0) {
            return false;
        }
        return !source.is(DamageTypes.CACTUS) && super.hurt(source, amount);
    }

    double storedXp() {
        return storedXp;
    }

    private void setStoredXp(double amount) {
        double minimum = isRainbow() ? MIN_RAINBOW_XP : 0;
        storedXp = Double.isFinite(amount) && Math.abs(amount) > PRECISION ? Math.max(minimum, amount) : 0;
        entityData.set(HAS_XP, storedXp > 0);
    }

    void absorb(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return;
        }
        double previousXp = storedXp;
        setStoredXp(storedXp + amount);
        double creditedXp = Math.min(amount, storedXp - previousXp);
        if (creditedXp > 0 && getHealth() < getMaxHealth()) {
            heal((float) Math.min(creditedXp, getMaxHealth() - getHealth()));
        }
    }

    double fundShield(double damage) {
        double covered = Math.min(damage, storedXp - MIN_RAINBOW_XP);
        setStoredXp(storedXp - covered);
        return covered;
    }

    boolean canSpend(double amount) {
        return storedXp > 0 && storedXp + PRECISION >= amount;
    }

    boolean spend(double amount) {
        if (!canSpend(amount)) {
            return false;
        }
        setStoredXp(storedXp - amount);
        return true;
    }

    boolean consumeTotem(long time) {
        if (isBaby() || !hasPower(LumenColor.GOLD) || time < totemCooldownUntil) {
            return false;
        }
        totemCooldownUntil = time + TOTEM_COOLDOWN;
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.UNLOADED_TO_CHUNK && level() instanceof ServerLevel level && getOwnerUUID() != null)
            LumenLedger.get(level).remember(getOwnerUUID(), this);
        super.remove(reason);
    }

    int followSlot(ServerLevel level) {
        UUID owner = getOwnerUUID();
        return owner == null || isOrderedToSit() ? -1 : LumenLedger.get(level).slot(owner, getUUID());
    }

    private void joinFollowers(ServerLevel level, UUID owner) {
        LumenLedger ledger = LumenLedger.get(level);
        boolean following = ledger.enlist(owner, getUUID());
        waitHere(!following);
        if (following) {
            ledger.remember(owner, this);
        }
    }

    boolean feetSupported() {
        return onGround() || level().getBlockCollisions(this,
                getBoundingBox().deflate(.01, 0, .01).move(0, -.025, 0)).iterator().hasNext();
    }

    private void waitHere(boolean waiting) {
        setOrderedToSit(waiting);
        entityData.set(WAITING, waiting);
        if (waiting) {
            setBubble(false);
            getNavigation().stop();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isTame() && !isRainbow() && held.is(color().plant().asItem())) {
            if (player instanceof ServerPlayer owner) {
                tame(player);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                joinFollowers(owner.serverLevel(), player.getUUID());
                level().broadcastEntityEvent(this, (byte) 7);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isOwnedBy(player)) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer owner) {
            ServerLevel level = owner.serverLevel();
            LumenLedger ledger = LumenLedger.get(level);
            if (player.isShiftKeyDown()) {
                if (!isOrderedToSit()) {
                    ledger.dismiss(player.getUUID(), getUUID());
                    waitHere(true);
                    owner.displayClientMessage(Component.translatable("message.yagens_mobs.lumen_wait"), true);
                } else if (ledger.enlist(player.getUUID(), getUUID())) {
                    waitHere(false);
                    ledger.remember(owner.getUUID(), this);
                    owner.displayClientMessage(Component.translatable("message.yagens_mobs.lumen_follow"), true);
                } else {
                    owner.displayClientMessage(Component.translatable("message.yagens_mobs.lumen_full"), true);
                }
            } else if (storedXp < 0) {
                double payment = ledger.takeForDebt(owner, -storedXp);
                setStoredXp(storedXp + payment);
                owner.displayClientMessage(Component.translatable("message.yagens_mobs.lumen_debt",
                        String.format(Locale.ROOT, "%.2f", payment), String.format(Locale.ROOT, "%.2f", -storedXp + 0.0)), true);
            } else if (storedXp > 0) {
                double amount = storedXp;
                setStoredXp(0);
                ledger.credit(level, player.getUUID(), amount);
                LumenExperienceParticle.flyTo(level, position().add(0, .24, 0), owner, 5);
                playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .5F, 1.2F);
                owner.displayClientMessage(Component.translatable("message.yagens_mobs.lumen_withdraw",
                        String.format(Locale.ROOT, "%.2f", amount)), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public boolean fliesWithOwner() {
        return isTame() && !entityData.get(WAITING) && (!isBaby() || inBubble());
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        if (!fliesWithOwner()) {
            return super.tickHeadTurn(yRot, animStep);
        }

        if (!level().isClientSide) {
            // Measure the travel that actually happened, including steering and collisions.
            double dx = getX() - xo;
            double dz = getZ() - zo;
            double horizontal = dx * dx + dz * dz;
            if (horizontal > 1.0e-6 && horizontal < 4) {
                setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
                float pitch = (float) (-Mth.atan2(getY() - yo, Math.sqrt(horizontal)) * Mth.RAD_TO_DEG);
                setXRot(Mth.clamp(pitch, -28, 28));
            }
        }
        // The client interpolates the tracked yaw. Vanilla body control has a higher movement threshold.
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        return animStep;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (getOwnerUUID() != null && !isOrderedToSit()) {
            LumenLedger.get(level).remember(getOwnerUUID(), this);
        }
        if (!isBaby() && !isNoAi() && (tickCount + getId()) % 20 == 0) {
            checkBreeding(level);
        }
    }

    LumenButterfly mate() {
        return mate;
    }

    private void checkBreeding(ServerLevel level) {
        long time = level.getGameTime();
        if (time < breedCooldownUntil) {
            return;
        }
        if (time >= breedReadyUntil) {
            mate = null;
            if (random.nextInt(100) != 0 || !hasPlant(level)) {
                return;
            }
            breedReadyUntil = time + BREED_WINDOW;
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + .45, getZ(), 3, .16, .1, .16, .01);
        }
        if (mate == null || !canMateWith(mate, time)) {
            mate = level.getEntitiesOfClass(LumenButterfly.class, getBoundingBox().inflate(BREED_RADIUS),
                            other -> canMateWith(other, time) && distanceToSqr(other) <= BREED_RADIUS * BREED_RADIUS)
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        if (mate != null && distanceToSqr(mate) <= 2.25) {
            breed(level, mate, time);
        }
    }

    private boolean canMateWith(LumenButterfly other, long time) {
        return other != this && other.isAlive() && !other.isBaby() && female != other.female
                && time < other.breedReadyUntil && time >= other.breedCooldownUntil
                && distanceToSqr(other) <= BREED_RADIUS * BREED_RADIUS;
    }

    private boolean hasPlant(ServerLevel level) {
        BlockPos center = blockPosition();
        Block plant = color().plant();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-BREED_RADIUS, -BREED_RADIUS, -BREED_RADIUS),
                center.offset(BREED_RADIUS, BREED_RADIUS, BREED_RADIUS))) {
            if (pos.distSqr(center) > BREED_RADIUS * BREED_RADIUS || !level.hasChunkAt(pos)) {
                continue;
            }
            Block candidate = level.getBlockState(pos).getBlock();
            if (candidate == plant || candidate instanceof FlowerPotBlock pot && pot.getContent() == plant) {
                return true;
            }
        }
        return false;
    }

    private void breed(ServerLevel level, LumenButterfly partner, long time) {
        LumenButterfly child = getBreedOffspring(level, partner);
        if (child != null) {
            Vec3 midpoint = position().lerp(partner.position(), .5);
            child.moveTo(midpoint.x, midpoint.y, midpoint.z, getYRot(), 0);
            if (level.addFreshEntity(child)) {
                if (child.getOwnerUUID() != null) {
                    child.joinFollowers(level, child.getOwnerUUID());
                }
                breedCooldownUntil = partner.breedCooldownUntil = time + BREED_COOLDOWN;
                breedReadyUntil = partner.breedReadyUntil = 0;
                mate = partner.mate = null;
                level.sendParticles(ParticleTypes.HEART, midpoint.x, midpoint.y + .35, midpoint.z, 8, .3, .15, .3, .03);
            }
        }
    }

    @Override
    public LumenButterfly getBreedOffspring(ServerLevel level, AgeableMob other) {
        if (!(other instanceof LumenButterfly partner)) {
            return null;
        }
        LumenButterfly child = LumenButterflies.TYPE.get().create(level);
        if (child != null) {
            int roll = random.nextInt(100);
            LumenButterfly mother = female ? this : partner;
            LumenButterfly father = female ? partner : this;
            LumenColor color = roll < 49 ? mother.color() : roll < 98 ? father.color() : LumenColor.random(random);
            child.setLineage(color, lineage() | partner.lineage());
            UUID owner = getOwnerUUID();
            if (isTame() && partner.isTame() && owner != null && owner.equals(partner.getOwnerUUID())) {
                child.setOwnerUUID(owner);
                child.setTame(true);
                child.waitHere(true);
            }
            child.female = random.nextBoolean();
            child.setBaby(true);
        }
        return child;
    }

    @Override
    public void die(DamageSource source) {
        boolean alreadyDead = dead;
        super.die(source);
        if (alreadyDead || !dead || !(level() instanceof ServerLevel level)) {
            return;
        }
        LumenLedger ledger = LumenLedger.get(level);
        UUID owner = getOwnerUUID();
        double amount = storedXp;
        setStoredXp(0);
        if (owner != null) {
            ledger.dismiss(owner, getUUID());
            ledger.credit(level, owner, amount);
            if (amount > 0 && level.getPlayerByUUID(owner) instanceof ServerPlayer player && player.isAlive()) {
                LumenExperienceParticle.flyTo(level, position().add(0, .25, 0), player, 6);
            }
        } else if (amount > 0) {
            dropWildXp(level, ledger.wildPayout(level, amount));
        }
    }

    private void dropWildXp(ServerLevel level, int xp) {
        int orbs = Math.min(xp, 2 + random.nextInt(2));
        if (xp <= orbs * Short.MAX_VALUE) {
            for (int i = 0; i < orbs; i++) {
                spawnOrb(level, i, orbs, xp / orbs + (i < xp % orbs ? 1 : 0), 1);
            }
            return;
        }

        // Vanilla serializes the orb value as a short. Its count stores repeated values separately.
        int stacks = xp / Short.MAX_VALUE;
        int rest = xp % Short.MAX_VALUE;
        int bigOrbs = orbs - (rest == 0 ? 0 : 1);
        for (int i = 0; i < bigOrbs; i++) {
            spawnOrb(level, i, orbs, Short.MAX_VALUE, stacks / bigOrbs + (i < stacks % bigOrbs ? 1 : 0));
        }
        if (rest > 0) {
            spawnOrb(level, bigOrbs, orbs, rest, 1);
        }
    }

    private void spawnOrb(ServerLevel level, int index, int total, int value, int count) {
        double angle = Math.PI * 2 * index / total;
        ExperienceOrb orb = new ExperienceOrb(level, getX() + .36 * Math.cos(angle), getY() + .25,
                getZ() + .36 * Math.sin(angle), value);
        ((LumenOrbAccess) orb).lumen$setCount(count);
        level.addFreshEntity(orb);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("LumenColor", color().id());
        tag.putInt("LumenLineage", lineage());
        tag.putDouble("LumenXp", storedXp);
        tag.putBoolean("Female", female);
        tag.putLong("BreedReadyUntil", breedReadyUntil);
        tag.putLong("BreedCooldownUntil", breedCooldownUntil);
        tag.putLong("TotemCooldownUntil", totemCooldownUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLineage(LumenColor.byId(tag.getString("LumenColor")), tag.getInt("LumenLineage"));
        setStoredXp(tag.getDouble("LumenXp"));
        female = tag.contains("Female") ? tag.getBoolean("Female") : random.nextBoolean();
        breedReadyUntil = tag.getLong("BreedReadyUntil");
        breedCooldownUntil = tag.getLong("BreedCooldownUntil");
        totemCooldownUntil = tag.getLong("TotemCooldownUntil");
        if (level() instanceof ServerLevel level && isAlive() && getOwnerUUID() != null && !isOrderedToSit()) {
            waitHere(!LumenLedger.get(level).enlist(getOwnerUUID(), getUUID()));
        } else {
            waitHere(isOrderedToSit());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, state -> {
            if (isBaby()) {
                return state.setAndContinue(state.isMoving() ? CRAWL : LARVA_REST);
            }
            return state.setAndContinue(entityData.get(WAITING) && feetSupported() ? PERCHED : FLIGHT);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

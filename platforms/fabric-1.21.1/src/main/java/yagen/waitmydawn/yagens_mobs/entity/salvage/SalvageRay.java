package yagen.waitmydawn.yagens_mobs.entity.salvage;

import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;

public final class SalvageRay extends AgeableMob implements GeoEntity, SilkLauncher.Courier {
    public static final List<String> COLORS = List.of("blue", "purple", "pink", "black", "albino");
    public static final double RIDER_DROP = 1.88;
    private static final double SERVICE_RANGE = 128;
    private static final double SEARCH_RANGE = 64;
    private static final double TRACKING_RANGE = 96;
    private static final double BREED_RANGE = 16;
    private static final double LURE_RANGE = 20;
    private static final float YOUNG_SCALE = .55F;
    private static final float YOUNG_HEALTH = 12;
    private static final float ADULT_HEALTH = 24;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SalvageRay.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TAILS = SynchedEntityData.defineId(SalvageRay.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> CARGO = SynchedEntityData.defineId(SalvageRay.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(SalvageRay.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BANK = SynchedEntityData.defineId(SalvageRay.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.salvage_ray.glide");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<BlockPos, UUID> known = new HashMap<>();
    private final Map<BlockPos, Long> blockedBoxes = new HashMap<>();
    private final SalvageTailPose tail = new SalvageTailPose();
    private final SalvageCargo carried = new SalvageCargo();
    private BlockPos home;
    private UUID homeId;
    private UUID offeredTo;
    private long offerUntil;
    private long rescueReady;
    private BlockPos destination;
    private UUID destinationId;
    private BlockPos delivery;
    private long deliveryUntil;
    private boolean urgentCargo;
    private Vec3 wanderTarget;
    private int wanderUntil;
    private double flightY;
    private ItemEntity targetItem;
    private ServerPlayer targetRescue;
    private Entity pursuit;
    private int pursuitTick;
    private Vec3 previousPursuitRay;
    private Vec3 previousPursuitTarget;
    private int nextMateSearch;
    private boolean breedReady;
    private UUID parentId;
    private SalvageRay mate;
    private SilkLauncher.Relay relay;
    private UUID relayPlayer;
    private Vec3 intended = Vec3.ZERO;
    private float prevPitch;
    private float prevBank;

    public SalvageRay(EntityType<? extends SalvageRay> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, ADULT_HEALTH).add(Attributes.ARMOR, 2)
                .add(Attributes.FLYING_SPEED, .5).add(Attributes.MOVEMENT_SPEED, .3);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CreatureCue.RAY_HURT.orElse(super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CreatureCue.RAY_DEATH.orElse(super.getDeathSound());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR, 0).define(TAILS, 1).define(CARGO, ItemStack.EMPTY).define(PITCH, 0F).define(BANK, 0F);
    }

    public void setVariant(int color, int tails) {
        entityData.set(COLOR, Mth.clamp(color, 0, COLORS.size() - 1));
        entityData.set(TAILS, Mth.clamp(tails, 1, 3));
    }

    public String color() { return COLORS.get(entityData.get(COLOR)); }
    public int tails() { return entityData.get(TAILS); }
    public ItemStack cargo() { return entityData.get(CARGO); }
    public SalvageTailPose tailPose() { return tail; }
    public float flightPitch(float tick) { return Mth.lerp(tick, prevPitch, entityData.get(PITCH)); }
    public float flightBank(float tick) { return Mth.lerp(tick, prevBank, entityData.get(BANK)); }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group) {
        int roll = random.nextInt(6);
        setVariant(random.nextInt(COLORS.size()), roll < 3 ? 1 : roll < 5 ? 2 : 3);
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    @Override
    public float getAgeScale() {
        return isBaby() ? YOUNG_SCALE : 1;
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        float fraction = getHealth() / getMaxHealth();
        applyAgeAttributes();
        setHealth(fraction * getMaxHealth());
        if (!isBaby()) {
            parentId = null;
            if (level() instanceof ServerLevel && isAlive()) {
                spawnAtLocation(ModItems.SALVAGE_RAY_MOLT.get());
            }
        }
    }

    private void applyAgeAttributes() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(isBaby() ? YOUNG_HEALTH : ADULT_HEALTH);
        getAttribute(Attributes.ARMOR).setBaseValue(isBaby() ? 0 : 2);
    }

    @Override
    public @Nullable SalvageRay getBreedOffspring(ServerLevel level, AgeableMob other) {
        if (!(other instanceof SalvageRay partner)) {
            return null;
        }
        SalvageRay child = SalvageRays.RAY.get().create(level);
        if (child != null) {
            SalvageRay parent = random.nextBoolean() ? this : partner;
            child.setVariant(parent.entityData.get(COLOR), parent.tails());
            child.setBaby(true);
            child.parentId = parent.getUUID();
            child.home = parent.home;
            child.homeId = parent.homeId;
            child.known.putAll(parent.known);
        }
        return child;
    }

    void setHome(SalvageNestBoxEntity box) {
        setHome(box.getBlockPos(), box.id());
    }

    private void setHome(BlockPos pos, UUID id) {
        home = pos;
        homeId = id;
        known.put(pos, id);
        wanderTarget = null;
    }

    private @Nullable SalvageNestDirectory.End origin() {
        return home == null || homeId == null ? null : new SalvageNestDirectory.End(home, homeId);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : carried.stacks()) {
            spawnAtLocation(stack.copy());
        }
        carried.clear();
        syncCargo();
        if (relay != null) {
            relay.cancel(level);
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (!level().isClientSide && isAlive()) {
            setDeltaMovement(intended);
            move(MoverType.SELF, intended);
        }
    }

    @Override
    public void tick() {
        float oldPitch = entityData.get(PITCH);
        float oldBank = entityData.get(BANK);
        if (level() instanceof ServerLevel level && isAlive() && !isNoAi()) {
            fly(level);
        } else {
            intended = Vec3.ZERO;
        }
        Vec3 previous = position();
        super.tick();
        prevPitch = oldPitch;
        prevBank = oldBank;
        if (level().isClientSide) {
            tail.tick(entityData.get(PITCH), entityData.get(BANK));
        } else {
            updatePose(position().subtract(previous));
            if (!getPassengers().isEmpty()) {
                for (Entity passenger : getPassengers()) {
                    positionRider(passenger);
                }
            }
        }
    }

    private void updatePose(Vec3 movement) {
        float turn = 0;
        if (movement.horizontalDistanceSqr() > .0004) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-movement.x, movement.z));
            turn = Mth.wrapDegrees(yaw - getYRot());
            setYRot(getYRot() + Mth.clamp(turn, -18, 18));
        }
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        float pitch = (float) Math.toDegrees(Mth.atan2(movement.y, Math.max(.025, movement.horizontalDistance())));
        entityData.set(PITCH, Mth.lerp(.22F, entityData.get(PITCH), Mth.clamp(pitch, -31, 31)));
        entityData.set(BANK, Mth.lerp(.18F, entityData.get(BANK), Mth.clamp(-turn * 2.8F, -27, 27)));
    }

    private void fly(ServerLevel level) {
        intended = Vec3.ZERO;
        if (pursuit != null && pursuitTick < tickCount - 1) {
            pursuit = null;
        }
        if (getFirstPassenger() instanceof ServerPlayer rider && destination != null) {
            flyPassenger(level, rider);
            return;
        }
        if (destination != null) {
            destination = null;
            destinationId = null;
        }
        if (relay != null) {
            if (!relay.alive(level)) {
                relay.cancel(level);
                relay = null;
            } else {
                Vec3 end = relay.approach();
                if (position().distanceToSqr(end) < 1.8 && relay.finish(level)) {
                    relay = null;
                } else {
                    moveToward(end, .50);
                }
                return;
            }
        }
        if (relayPlayer != null) {
            ServerPlayer preparing = level.getServer().getPlayerList().getPlayer(relayPlayer);
            if (preparing != null && preparing.level() == level && preparing.isAlive() && distanceToSqr(preparing) <= 100
                    && SilkLauncher.isSelectedCourier(preparing, this)) {
                return;
            }
            relayPlayer = null;
        }
        if (offeredTo != null) {
            ServerPlayer choosing = level.getServer().getPlayerList().getPlayer(offeredTo);
            if (choosing != null && choosing.level() == level && canOffer(choosing)) {
                return;
            }
            offeredTo = null;
        }
        SalvageNestBoxEntity base = home == null ? null : SalvageNestDirectory.loaded(level, home);
        if (base != null && !base.id().equals(homeId)) {
            base = null;
        }
        if (isBaby()) {
            followParent(level, base);
            return;
        }
        if ((tickCount + getId()) % 4 == 0) {
            scan(level, base);
        }
        if (targetRescue != null && rescue(level, base)) {
            return;
        }
        if (targetItem != null && fallsIntoVoid(level, targetItem)) {
            interruptFreight(level);
            if (chaseFall(targetItem, new Vec3(0, .12, 0), 1.0, 1.15)) {
                collect(level, targetItem, true);
            }
            return;
        }
        if (base != null && base.guard() && guard(level, base)) {
            return;
        }
        if (!carried.isEmpty()) {
            deposit(level);
            return;
        }
        if (targetItem != null && onGround(level, targetItem)) {
            if (getBoundingBox().inflate(.65).intersects(targetItem.getBoundingBox())
                    && SalvageFlight.lineOfSight(this, targetItem.position())) {
                collect(level, targetItem, false);
            } else {
                moveToward(targetItem.position().add(0, .12, 0), .43);
            }
            return;
        }
        targetItem = null;
        if (breedReady && seekMate(level)) {
            return;
        }
        if (base == null && tickCount % 40 == 0) {
            for (var site : SalvageNestDirectory.of(level).near(position(), 32)) {
                SalvageNestBoxEntity found = SalvageNestDirectory.loaded(level, site.pos());
                if (found != null && found.id().equals(known.get(site.pos()))) {
                    setHome(found);
                    base = found;
                    break;
                }
            }
        }
        if (!followFruit(level, base)) {
            roam(base);
        }
    }

    private void scan(ServerLevel level, @Nullable SalvageNestBoxEntity base) {
        if (targetRescue == null && base != null && level.getGameTime() >= rescueReady) {
            targetRescue = level.players().stream()
                    .filter(player -> distanceToSqr(player) <= SEARCH_RANGE * SEARCH_RANGE && falling(level, base, player))
                    .min(Comparator.comparing((ServerPlayer player) -> !SalvageFlight.overVoid(level, player.position()))
                            .thenComparingDouble(this::distanceToSqr)).orElse(null);
        }
        if (targetRescue != null || urgentCargo || targetItem != null && fallsIntoVoid(level, targetItem)) {
            return;
        }

        var nearby = getBoundingBox().inflate(SEARCH_RANGE);
        var items = level.getEntitiesOfClass(ItemEntity.class, nearby, item ->
                distanceToSqr(item) <= SEARCH_RANGE * SEARCH_RANGE && canCollect(level, item)
                        && (item.onGround() || item.getDeltaMovement().y < -.04));
        if (items.isEmpty()) {
            return;
        }
        var collectors = level.getEntitiesOfClass(SalvageRay.class, nearby, ray -> ray != this && ray.targetItem != null);
        var available = items.stream().filter(item -> collectors.stream().noneMatch(ray -> ray.targetItem == item)).toList();
        var emergency = available.stream().filter(item -> fallsIntoVoid(level, item))
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (emergency != null) {
            targetItem = emergency;
        } else if (carried.isEmpty() && (targetItem == null || !onGround(level, targetItem))) {
            targetItem = available.stream().filter(item -> onGround(level, item))
                    .min(Comparator.comparing((ItemEntity item) -> !item.getItem().is(Items.CHORUS_FRUIT))
                            .thenComparingDouble(this::distanceToSqr)).orElse(null);
        }
    }

    private boolean canCollect(ServerLevel level, ItemEntity item) {
        return item.isAlive() && SalvageSpill.canCollect(level, item);
    }

    private boolean onGround(ServerLevel level, ItemEntity item) {
        return item.onGround() && canCollect(level, item) && distanceToSqr(item) <= TRACKING_RANGE * TRACKING_RANGE;
    }

    private boolean fallsIntoVoid(ServerLevel level, ItemEntity item) {
        return canCollect(level, item) && !item.onGround() && item.getDeltaMovement().y < -.04
                && distanceToSqr(item) < TRACKING_RANGE * TRACKING_RANGE && SalvageFlight.overVoid(level, item.position());
    }

    private boolean falling(ServerLevel level, SalvageNestBoxEntity base, ServerPlayer player) {
        if (!base.allowsRescue(player.getUUID()) || player.isSpectator() || player.getAbilities().flying
                || player.isPassenger() || !player.isAlive() || player.onGround() || player.isFallFlying()
                || distanceToSqr(player) > TRACKING_RANGE * TRACKING_RANGE) {
            return false;
        }
        double descent = player.getKnownMovement().y;
        return (descent < -.16 && SalvageFlight.overVoid(level, player.position()))
                || descent < -.8 && (player.fallDistance >= 8 || SalvageFlight.clearBelow(level, player.position(), 8));
    }

    private boolean rescue(ServerLevel level, @Nullable SalvageNestBoxEntity base) {
        ServerPlayer player = targetRescue;
        if (base == null || !falling(level, base, player)) {
            targetRescue = null;
            return false;
        }
        interruptFreight(level);
        if (chaseFall(player, new Vec3(0, RIDER_DROP, 0), 1.48, 1.35)
                && SalvageFlight.landing(level, base.getBlockPos()) != null && player.startRiding(this, true)) {
            setDestination(base.getBlockPos(), base.id());
            CreatureCue.RAY_CATCH.play(this, .8F);
            rescueReady = level.getGameTime() + 600;
            targetRescue = null;
        }
        return true;
    }

    private void interruptFreight(ServerLevel level) {
        if (!urgentCargo && !carried.isEmpty()) {
            spill(level, position());
            delivery = null;
        }
    }

    private void collect(ServerLevel level, ItemEntity first, boolean emergency) {
        Vec3 point = first.position();
        if (!carried.take(first)) return;
        CreatureCue.RAY_CATCH.play(this, .65F);
        if (carried.hasRoom()) {
            var nearby = level.getEntitiesOfClass(ItemEntity.class, first.getBoundingBox().inflate(2.5),
                    item -> item != first && canCollect(level, item) && item.distanceToSqr(point) <= 2.5 * 2.5
                            && (item.onGround() || emergency && fallsIntoVoid(level, item)));
            nearby.sort(Comparator.comparingDouble(item -> item.distanceToSqr(point)));
            for (ItemEntity item : nearby) {
                if (!carried.hasRoom()) {
                    break;
                }
                if (SalvageFlight.lineOfSight(this, item.position())) {
                    carried.take(item);
                }
            }
        }
        urgentCargo = emergency;
        targetItem = null;
        syncCargo();
    }

    private void syncCargo() { entityData.set(CARGO, carried.display()); }

    private void spill(ServerLevel level, Vec3 point) {
        for (ItemStack stack : carried.stacks()) {
            spawnSpill(level, point, stack, null);
        }
        carried.clear();
        urgentCargo = false;
        syncCargo();
    }

    private static void spawnSpill(ServerLevel level, Vec3 point, ItemStack stack, @Nullable BlockPos box) {
        if (!stack.isEmpty()) {
            var item = new ItemEntity(level, point.x, point.y, point.z, stack, 0, 0, 0);
            item.setPickUpDelay(10);
            // Overflow stays available to players without a loop of regathering and resetting despawn.
            SalvageSpill.mark(item, level.getGameTime() + 160, box);
            level.addFreshEntity(item);
        }
    }

    private boolean chaseFall(Entity target, Vec3 offset, double reach, double cruise) {
        Vec3 point = target.position().add(offset);
        Vec3 current = position();
        boolean continuous = pursuit == target && pursuitTick == tickCount - 1;
        Vec3 motion = target.getKnownMovement();
        if (continuous) {
            Vec3 travelled = point.subtract(previousPursuitTarget);
            if (travelled.y < motion.y) {
                motion = travelled;
            }
        }
        boolean contact = SalvageFlight.sweptContact(continuous ? previousPursuitRay : current, current,
                continuous ? previousPursuitTarget : point, point, reach);
        previousPursuitRay = current;
        previousPursuitTarget = point;
        pursuitTick = tickCount;
        pursuit = target;
        if (contact && SalvageFlight.lineOfSight(this, target.getEyePosition())) {
            pursuit = null;
            return true;
        }
        intended = SalvageFlight.intercept(this, target, point, motion, cruise);
        return false;
    }

    private void followParent(ServerLevel level, @Nullable SalvageNestBoxEntity base) {
        if (parentId != null && level.getEntity(parentId) instanceof SalvageRay parent && parent.isAlive()
                && distanceToSqr(parent) < SEARCH_RANGE * SEARCH_RANGE) {
            Vec3 behind = Vec3.directionFromRotation(0, parent.getYRot()).scale(-2);
            moveToward(parent.position().add(behind).add(0, -.15, 0), .55);
        } else if (base != null) {
            roam(base);
        } else {
            roam(null);
        }
    }

    private boolean availableToBreed() {
        return breedReady && !isBaby() && isAlive() && !isNoAi() && !isVehicle() && relay == null && offeredTo == null
                && targetRescue == null && targetItem == null && carried.isEmpty();
    }

    private boolean seekMate(ServerLevel level) {
        if (mate != null && (!mate.availableToBreed() || distanceToSqr(mate) > BREED_RANGE * BREED_RANGE)) {
            mate.mate = null;
            mate = null;
        }
        if (mate == null && tickCount >= nextMateSearch) {
            nextMateSearch = tickCount + 10;
            mate = level.getEntitiesOfClass(SalvageRay.class, getBoundingBox().inflate(BREED_RANGE),
                            ray -> ray != this && ray.availableToBreed() && distanceToSqr(ray) <= BREED_RANGE * BREED_RANGE
                                    && (ray.mate == null || ray.mate == this))
                    .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (mate != null) {
                mate.mate = this;
            }
        }
        if (mate == null) {
            return false;
        }
        if (distanceToSqr(mate) > 3 || !SalvageFlight.lineOfSight(this, mate.getEyePosition())) {
            moveToward(mate.position(), .28);
        } else {
            SalvageRay child = getBreedOffspring(level, mate);
            if (child != null) {
                Vec3 birth = position().lerp(mate.position(), .5).add(0, -.35, 0);
                child.moveTo(birth.x, birth.y, birth.z, getYRot(), 0);
                if (level.noCollision(child) && level.addFreshEntity(child)) {
                    breedReady = false;
                    mate.breedReady = false;
                    mate.mate = null;
                    mate = null;
                    level.sendParticles(ParticleTypes.HEART, birth.x, birth.y + .5, birth.z, 8, .5, .25, .5, .02);
                }
            }
        }
        return true;
    }

    private boolean guard(ServerLevel level, SalvageNestBoxEntity base) {
        if (distanceToSqr(base.servicePoint()) > 20 * 20) {
            return false;
        }
        var bullets = level.getEntitiesOfClass(ShulkerBullet.class, getBoundingBox().inflate(13),
                bullet -> bullet.distanceToSqr(base.servicePoint()) < 20 * 20);
        ShulkerBullet bullet = bullets.stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (bullet == null) {
            return false;
        }
        if (getBoundingBox().inflate(.25).intersects(bullet.getBoundingBox())) {
            bullet.hurt(damageSources().mobAttack(this), 1);
        } else {
            moveToward(bullet.position(), .74);
        }
        return true;
    }

    private void deposit(ServerLevel level) {
        long time = level.getGameTime();
        if (delivery != null && time >= deliveryUntil) {
            blockedBoxes.put(delivery, time + 100);
            delivery = null;
        }
        SalvageNestBoxEntity box = delivery == null ? null : SalvageNestDirectory.loaded(level, delivery);
        if (box == null) {
            delivery = null;
            if (tickCount % 12 == 0) {
                blockedBoxes.values().removeIf(expiry -> expiry <= time);
                if (home != null && distanceToSqr(Vec3.atCenterOf(home)) <= SERVICE_RANGE * SERVICE_RANGE
                        && !blockedBoxes.containsKey(home)) {
                    var resident = SalvageNestDirectory.loaded(level, home);
                    if (resident != null && resident.id().equals(homeId)) {
                        box = resident;
                    }
                }
                if (box == null) {
                    for (var site : SalvageNestDirectory.of(level).near(position(), SERVICE_RANGE)) {
                        var nearby = SalvageNestDirectory.loaded(level, site.pos());
                        if (nearby != null && !blockedBoxes.containsKey(site.pos())) {
                            box = nearby;
                            break;
                        }
                    }
                }
                if (box != null) {
                    delivery = box.getBlockPos();
                    deliveryUntil = time + 160 + (long) (position().distanceTo(box.servicePoint()) * 3);
                }
            }
        }
        if (box != null) {
            if (distanceToSqr(box.servicePoint()) < 1.35 && SalvageFlight.lineOfSight(this, box.perch())) {
                deliver(level, box);
                delivery = null;
            } else if (getY() < box.servicePoint().y - .6) {
                moveToward(SalvageFlight.climbApproach(this, box.servicePoint().y + 1, false), .85);
            } else {
                moveToward(box.servicePoint(), .47);
            }
        } else if (home != null) {
            var point = Vec3.atBottomCenterOf(home).add(0, 4, 0);
            moveToward(point, .34);
        }
    }

    private void deliver(ServerLevel level, SalvageNestBoxEntity box) {
        // The delivery must have been to a known box before this meal discovers new ones.
        boolean knownBox = box.id().equals(known.get(box.getBlockPos()));
        if (carried.eatFruit()) {
            regenerateFromFruit();
            recognize(level, Vec3.atCenterOf(box.getBlockPos()));
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + .5, getZ(), 4, .4, .2, .4, .02);
        }
        if (!carried.isEmpty()) CreatureCue.RAY_DEPOSIT.play(this, .65F);
        int inserted = 0;
        for (ItemStack stack : carried.stacks()) {
            inserted += box.deposit(stack);
            spawnSpill(level, Vec3.atBottomCenterOf(box.getBlockPos().above()).add(0, .08, 0), stack, box.getBlockPos());
        }
        carried.clear();
        urgentCargo = false;
        syncCargo();
        if (knownBox) {
            rollBreeding(level, box, inserted);
        }
    }

    private void rollBreeding(ServerLevel level, SalvageNestBoxEntity box, int inserted) {
        if (inserted <= 0 || breedReady || !box.hasChorusFruit() || random.nextDouble() >= (5 + inserted / 8.0) / 100) {
            return;
        }
        box.consumeChorusFruit();
        regenerateFromFruit();
        breedReady = true;
        level.sendParticles(ParticleTypes.HEART, getX(), getY() + .5, getZ(), 4, .4, .2, .4, .02);
    }

    private void regenerateFromFruit() {
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 1));
    }

    private void recognize(ServerLevel level, Vec3 meal) {
        var directory = SalvageNestDirectory.of(level);
        for (var site : directory.near(meal, 8)) {
            known.put(site.pos(), site.id());
            if (origin() == null || directory.resolve(origin()) == null) {
                setHome(site.pos(), site.id());
            }
        }
    }

    private boolean followFruit(ServerLevel level, @Nullable SalvageNestBoxEntity base) {
        Vec3 center = rangeCenter(base);
        var owner = level.players().stream().filter(player -> !player.isSpectator() && player.isAlive()
                && player.isHolding(Items.CHORUS_FRUIT) && distanceToSqr(player) <= LURE_RANGE * LURE_RANGE
                && (center == null || player.distanceToSqr(center) < SEARCH_RANGE * SEARCH_RANGE))
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (owner == null) {
            return false;
        }
        if (distanceToSqr(owner) > 6) {
            Vec3 point = owner.getEyePosition().add(0, .5, 0);
            moveToward(center == null ? point : withinRoam(center, point), .32);
        }
        return true;
    }

    private @Nullable Vec3 rangeCenter(@Nullable SalvageNestBoxEntity base) {
        return base != null ? base.servicePoint() : home == null ? null : Vec3.atBottomCenterOf(home).add(0, 3.14, 0);
    }

    private void roam(@Nullable SalvageNestBoxEntity base) {
        Vec3 center = rangeCenter(base);
        if (center == null) {
            if (wanderTarget == null || tickCount >= wanderUntil || distanceToSqr(wanderTarget) < 1) {
                wanderTarget = position().add(random.nextDouble() * 6 - 3, random.nextDouble() - .5, random.nextDouble() * 6 - 3);
                wanderUntil = tickCount + 50;
            }
            moveToward(wanderTarget, .16);
            return;
        }
        if (wanderTarget == null || tickCount >= wanderUntil || distanceToSqr(wanderTarget) < 1) {
            double heading = random.nextDouble() * Math.PI * 2;
            double distance = 4 + random.nextDouble() * 45;
            wanderTarget = center.add(Math.cos(heading) * distance, 1 + random.nextDouble() * 8, Math.sin(heading) * distance);
            wanderUntil = tickCount + 95 + random.nextInt(100);
        }
        if ((tickCount + getId()) % 180 == 0 && distanceToSqr(center) <= SEARCH_RANGE * SEARCH_RANGE) {
            CreatureCue.RAY_IDLE.play(this, .55F);
        }
        Vec3 target = withinRoam(center, wanderTarget);
        moveToward(target, .28);
        if (position().add(intended).distanceToSqr(center) > SEARCH_RANGE * SEARCH_RANGE) {
            moveToward(center, .32);
        }
    }

    private static Vec3 withinRoam(Vec3 center, Vec3 point) {
        Vec3 delta = point.subtract(center);
        return delta.lengthSqr() <= 62 * 62 ? point : center.add(delta.normalize().scale(62));
    }

    private void moveToward(Vec3 point, double speed) {
        if (distanceToSqr(point) > .01) {
            intended = SalvageFlight.steer(this, point, speed, isVehicle());
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof SilkLauncher && SilkLauncher.pendingPin(player, held) != null) {
            if (player instanceof ServerPlayer owner && !SilkLauncher.selectCourier(owner, held, this)) {
                owner.displayClientMessage(Component.translatable(isBaby() ? "message.yagens_mobs.salvage_ray.young"
                        : "message.yagens_mobs.salvage_ray.busy"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!held.is(Items.CHORUS_FRUIT)) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer owner) {
            if (isBaby()) {
                owner.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.young"), true);
                return InteractionResult.CONSUME;
            }
            if (isVehicle() || relay != null || relayPlayer != null || offeredTo != null && !canOffer(owner)) {
                owner.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.busy"), true);
                return InteractionResult.CONSUME;
            }
            if (routes(owner.serverLevel()).isEmpty()) {
                owner.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.no_nests"), true);
            } else {
                offeredTo = owner.getUUID();
                offerUntil = owner.serverLevel().getGameTime() + 400;
                SalvageRoutes.open(owner, this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    List<SalvageNestDirectory.Site> routes(ServerLevel level) {
        var origin = origin();
        return origin == null ? List.of() : SalvageNestDirectory.of(level).neighbors(origin);
    }

    boolean canOffer(ServerPlayer player) {
        return player.getUUID().equals(offeredTo) && player.isAlive() && !player.isSpectator()
                && player.serverLevel().getGameTime() <= offerUntil && player.isHolding(Items.CHORUS_FRUIT)
                && distanceToSqr(player) <= 64 && isAlive() && !isVehicle() && relay == null;
    }

    boolean beginRide(ServerPlayer player, BlockPos pos) {
        var directory = SalvageNestDirectory.of(player.serverLevel());
        var origin = origin();
        var site = directory.at(pos);
        if (!canOffer(player) || origin == null || site == null
                || !directory.connected(origin, SalvageNestDirectory.End.of(site)) || player.isPassenger()
                || !player.startRiding(this, true)) {
            return false;
        }
        offeredTo = null;
        setDestination(pos, site.id());
        return true;
    }

    private void setDestination(BlockPos pos, UUID id) {
        destination = pos;
        destinationId = id;
        flightY = Math.max(getY(), pos.getY() + 9);
    }

    private void flyPassenger(ServerLevel level, ServerPlayer player) {
        var site = SalvageNestDirectory.of(level).at(destination);
        if (site == null || !site.id().equals(destinationId)) {
            var safe = SalvageFlight.landing(level, blockPosition());
            if (safe != null) {
                finishRide(player, safe);
            }
            return;
        }
        Vec3 goal = Vec3.atCenterOf(destination);
        if (getY() < goal.y - 1) {
            moveToward(SalvageFlight.climbApproach(this, goal.y + 5, true), .85);
            return;
        }
        Vec3 safe = position().subtract(goal).horizontalDistanceSqr() < 16 * 16
                ? SalvageFlight.landing(level, destination) : null;
        if (safe != null) {
            Vec3 arrival = safe.add(0, RIDER_DROP + .18, 0);
            if (distanceToSqr(arrival) < .35) {
                setHome(site.pos(), site.id());
                finishRide(player, safe);
            } else {
                moveToward(arrival, .36);
            }
        } else {
            moveToward(new Vec3(goal.x, flightY, goal.z), .55);
        }
    }

    private void finishRide(ServerPlayer player, Vec3 safe) {
        player.stopRiding();
        player.dismountTo(safe.x, safe.y, safe.z);
        player.resetFallDistance();
        destination = null;
        destinationId = null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        move.accept(passenger, getX(), getY() - RIDER_DROP, getZ());
        passenger.resetFallDistance();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isBaby() && !isVehicle() && passenger instanceof Player;
    }

    @Override
    public boolean canRelay() {
        return isAlive() && !isBaby() && !isVehicle() && relay == null && offeredTo == null && carried.isEmpty();
    }

    @Override
    public boolean reserveRelay(ServerPlayer player) {
        if (!canRelay() || relayPlayer != null && !relayPlayer.equals(player.getUUID())) {
            return false;
        }
        relayPlayer = player.getUUID();
        return true;
    }

    @Override
    public void acceptRelay(SilkLauncher.Relay job) {
        relayPlayer = null;
        relay = job;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 0, state -> state.setAndContinue(GLIDE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Color", entityData.get(COLOR));
        tag.putInt("Tails", tails());
        tag.put("CargoStacks", carried.save(registryAccess()));
        tag.putBoolean("UrgentCargo", urgentCargo);
        tag.putLong("RescueReady", rescueReady);
        tag.putBoolean("BreedReady", breedReady);
        if (parentId != null) {
            tag.putUUID("Parent", parentId);
        }
        if (home != null && homeId != null) {
            tag.putLong("Home", home.asLong());
            tag.putUUID("HomeId", homeId);
        }
        if (destination != null && destinationId != null) {
            tag.putLong("Destination", destination.asLong());
            tag.putUUID("DestinationId", destinationId);
            tag.putDouble("FlightY", flightY);
        }
        if (relay != null) {
            tag.put("Relay", relay.save());
        }
        ListTag routes = new ListTag();
        known.forEach((pos, id) -> {
            CompoundTag knownBox = new CompoundTag();
            knownBox.putLong("Pos", pos.asLong());
            knownBox.putUUID("Id", id);
            routes.add(knownBox);
        });
        tag.put("Routes", routes);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Color"), tag.getInt("Tails"));
        carried.read(tag, registryAccess());
        urgentCargo = tag.getBoolean("UrgentCargo");
        syncCargo();
        rescueReady = tag.getLong("RescueReady");
        applyAgeAttributes();
        breedReady = !isBaby() && tag.getBoolean("BreedReady");
        parentId = tag.hasUUID("Parent") && isBaby() ? tag.getUUID("Parent") : null;
        homeId = tag.hasUUID("HomeId") ? tag.getUUID("HomeId") : null;
        home = homeId == null ? null : BlockPos.of(tag.getLong("Home"));
        destinationId = tag.hasUUID("DestinationId") ? tag.getUUID("DestinationId") : null;
        destination = destinationId == null ? null : BlockPos.of(tag.getLong("Destination"));
        flightY = tag.getDouble("FlightY");
        relay = tag.contains("Relay", Tag.TAG_COMPOUND) ? SilkLauncher.Relay.load(tag.getCompound("Relay")) : null;
        known.clear();
        for (Tag box : tag.getList("Routes", Tag.TAG_COMPOUND)) {
            CompoundTag knownBox = (CompoundTag) box;
            if (knownBox.hasUUID("Id")) {
                known.put(BlockPos.of(knownBox.getLong("Pos")), knownBox.getUUID("Id"));
            }
        }
    }
}

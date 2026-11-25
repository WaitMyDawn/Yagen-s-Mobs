package yagen.waitmydawn.yagens_mobs.entity.desertskate;

import java.util.Comparator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

final class SkateBreeding {
    private static final int READY_TICKS = 60 * 20;
    private static final int COOLDOWN_TICKS = 20 * 60 * 20;
    private static final double SEARCH_RANGE = 16.0;
    private static final double BREED_DISTANCE = 1.8;
    private final DesertSkate skate;
    @Nullable private SkateBreeding mate;
    private long readyUntil;
    private long cooldownUntil;
    private int nextSearch;
    private int nextApproach;

    SkateBreeding(DesertSkate skate) {
        this.skate = skate;
    }

    void afterMeal() {
        long now = skate.level().getGameTime();
        if (now >= cooldownUntil) {
            readyUntil = now + READY_TICKS;
            hearts();
        }
    }

    boolean isReady() {
        long now = skate.level().getGameTime();
        return !skate.isBaby() && now >= cooldownUntil && now < readyUntil;
    }

    private boolean canCourt() {
        return isReady() && skate.isAlive() && !skate.isNoAi()
                && skate.burrowPhase() == DesertSkate.BurrowPhase.SURFACE;
    }

    boolean tick() {
        if (!canCourt()) {
            leavePair();
            return false;
        }
        if (mate != null && (!mate.canCourt() || !isNearby(mate.skate))) {
            leavePair();
        }
        if (mate == null && skate.tickCount >= nextSearch) {
            nextSearch = skate.tickCount + 20;
            seekMate();
        }
        if (mate == null) {
            return false;
        }
        DesertSkate other = mate.skate;
        if (other.isEating()) {
            skate.getNavigation().stop();
            return true;
        }
        skate.getLookControl().setLookAt(other, 35.0F, 30.0F);
        if (withinBreedDistance(other)) {
            breed((ServerLevel) skate.level(), mate);
            leavePair();
            return true;
        }
        if (skate.tickCount >= nextApproach) {
            nextApproach = skate.tickCount + 8;
            var path = skate.getNavigation().createPath(other, 0);
            if (path == null || !path.canReach() || !skate.getNavigation().moveTo(path, 1.0)) {
                leavePair();
                return false;
            }
        }
        return true;
    }

    private void seekMate() {
        skate.level().getEntitiesOfClass(DesertSkate.class, skate.getBoundingBox().inflate(SEARCH_RANGE),
                        other -> other != skate && isNearby(other) && other.breeding().mate == null
                                && other.breeding().canCourt() && !other.isEating())
                .stream().sorted(Comparator.comparingDouble(skate::distanceToSqr))
                .filter(this::canApproach)
                .findFirst().ifPresent(other -> {
                    // Reserve both parents before either can select another mate on its next AI tick.
                    mate = other.breeding();
                    mate.mate = this;
                    nextApproach = skate.tickCount;
                    mate.nextApproach = other.tickCount;
                });
    }

    private boolean isNearby(DesertSkate other) {
        return other.level() == skate.level() && skate.distanceToSqr(other) <= SEARCH_RANGE * SEARCH_RANGE;
    }

    private boolean withinBreedDistance(DesertSkate other) {
        return skate.distanceToSqr(other) <= BREED_DISTANCE * BREED_DISTANCE && skate.getSensing().hasLineOfSight(other);
    }

    private boolean canApproach(DesertSkate other) {
        if (withinBreedDistance(other)) {
            return true;
        }
        var path = skate.getNavigation().createPath(other, 0);
        return path != null && path.canReach();
    }

    private void breed(ServerLevel level, SkateBreeding other) {
        DesertSkate child = ModEntities.DESERT_SKATE.get().create(level);
        if (child == null) {
            return;
        }
        child.setBaby(true);
        child.setPersistenceRequired();
        Vec3 midpoint = skate.position().lerp(other.skate.position(), 0.5);
        placeChild(child, midpoint);
        if (!canPlaceChild(level, child)) {
            placeChild(child, skate.position());
            if (!canPlaceChild(level, child)) {
                return;
            }
        }
        EventHooks.finalizeMobSpawn(child, level, level.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.BREEDING, null);
        if (level.addFreshEntity(child)) {
            long cooldownEnd = level.getGameTime() + COOLDOWN_TICKS;
            readyUntil = other.readyUntil = 0;
            cooldownUntil = other.cooldownUntil = cooldownEnd;
            hearts();
            other.hearts();
        }
    }

    private void placeChild(DesertSkate child, Vec3 position) {
        child.moveTo(position.x, position.y, position.z, skate.getYRot(), 0.0F);
    }

    private boolean canPlaceChild(ServerLevel level, DesertSkate child) {
        var bounds = child.getBoundingBox();
        return level.getWorldBorder().isWithinBounds(bounds)
                && !level.getBlockCollisions(child, bounds).iterator().hasNext();
    }

    void leavePair() {
        if (mate == null) {
            return;
        }
        mate.mate = null;
        mate.nextSearch = mate.skate.tickCount + 20;
        mate.skate.getNavigation().stop();
        mate = null;
        nextSearch = skate.tickCount + 20;
        skate.getNavigation().stop();
    }

    private void hearts() {
        ((ServerLevel) skate.level()).sendParticles(ParticleTypes.HEART, skate.getX(), skate.getY() + 0.55,
                skate.getZ(), 5, 0.25, 0.15, 0.25, 0.0);
    }

    void save(CompoundTag tag) {
        long now = skate.level().getGameTime();
        if (readyUntil > now) {
            tag.putLong("BreedReadyUntil", readyUntil);
        }
        if (cooldownUntil > now) {
            tag.putLong("BreedCooldownUntil", cooldownUntil);
        }
    }

    void load(CompoundTag tag) {
        leavePair();
        readyUntil = tag.getLong("BreedReadyUntil");
        cooldownUntil = tag.getLong("BreedCooldownUntil");
    }
}

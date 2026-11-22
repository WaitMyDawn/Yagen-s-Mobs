package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.DesertSkate.BurrowPhase;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;

final class SkateBurrow {
    private static final double SWIM_SPEED = 0.16;
    static final double SUBMERGED_OFFSET = 0.78;
    private static final int MAX_DEPTH = 4;
    private static final float[] SWIM_TURNS = {0.0F, 0.65F, -0.65F, 1.3F, -1.3F, 2.2F, -2.2F, Mth.PI};
    private final DesertSkate skate;
    private Vec3 heading = Vec3.ZERO;
    private int turnAt;
    private Vec3 transitionFrom = Vec3.ZERO;
    private Vec3 transitionTo = Vec3.ZERO;
    @Nullable private Vec3 lastTrailPosition;

    SkateBurrow(DesertSkate skate) {
        this.skate = skate;
    }

    boolean enter(boolean animate) {
        var support = BlockPos.containing(skate.getX(), skate.getY() + 0.05, skate.getZ()).below();
        if (skate.level().isRaining() || Math.abs(skate.getY() - (support.getY() + 1.0)) > 0.12) {
            return false;
        }
        var inside = new Vec3(skate.getX(), support.getY() + 1.0 - SUBMERGED_OFFSET, skate.getZ());
        if (!canSwimAt(inside) || !canPassTo(inside)) {
            return false;
        }
        if (animate) {
            sandBurst(skate.position());
            begin(BurrowPhase.DIVING, inside);
        } else {
            skate.setPos(inside);
            skate.setBurrowPhase(BurrowPhase.BURIED);
        }
        return true;
    }

    private boolean isStillInSand() {
        return canSwimAt(skate.position());
    }

    void tick() {
        switch (skate.burrowPhase()) {
            case BURIED, AMBUSHING -> {
                if (!isStillInSand()) {
                    revealAfterBlockChange();
                } else if (skate.level().isRaining()) {
                    Vec3 exit = surface();
                    if (exit != null) {
                        emerge(exit);
                    }
                }
            }
            case DIVING -> {
                if (skate.level().isRaining() && isOpenAt(transitionFrom) && canPassTo(transitionFrom)) {
                    emerge(transitionFrom);
                } else if (skate.level().isRaining() || !canSwimAt(transitionTo)) {
                    revealAfterBlockChange();
                } else {
                    advance(BurrowPhase.BURIED);
                }
            }
            case EMERGING -> {
                if (!isOpenAt(transitionTo)) {
                    revealAfterBlockChange();
                } else {
                    advance(BurrowPhase.SURFACE);
                }
            }
            case SURFACE -> {
            }
        }
    }

    private void advance(BurrowPhase finishedPhase) {
        // Sand can be removed or replaced while the skate is crossing the ground.
        if (!canPassTo(transitionTo)) {
            revealAfterBlockChange();
            return;
        }
        float progress = skate.burrowProgress(0.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        skate.setPos(transitionFrom.lerp(transitionTo, eased));
        if (progress >= 1.0F) {
            skate.setBurrowPhase(finishedPhase);
            if (finishedPhase == BurrowPhase.SURFACE) {
                sandBurst(transitionTo);
            }
        }
    }

    void emerge(Vec3 exit) {
        begin(BurrowPhase.EMERGING, exit);
    }

    private void begin(BurrowPhase phase, Vec3 destination) {
        transitionFrom = skate.position();
        transitionTo = destination;
        skate.setBurrowPhase(phase);
    }

    private void revealAfterBlockChange() {
        // A newly exposed body must use normal physics at its real location immediately.
        if (isOpenAt(skate.position())) {
            skate.setBurrowPhase(BurrowPhase.SURFACE);
            return;
        }
        Vec3 exit = surface();
        if (exit != null) {
            emerge(exit);
        } else {
            skate.setBurrowPhase(isStillInSand() ? BurrowPhase.BURIED : BurrowPhase.SURFACE);
        }
    }

    private void sandBurst(Vec3 exit) {
        var level = (ServerLevel) skate.level();
        var sand = level.getBlockState(BlockPos.containing(exit).below());
        if (sand.is(ModTags.BURROWABLE_SAND)) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand),
                    exit.x, exit.y + 0.15, exit.z, 16, 0.45, 0.18, 0.45, 0.11);
            skate.playSound(SoundEvents.SAND_BREAK, 1.0F, 0.75F);
        }
    }

    void warnAmbush(Vec3 exit, float progress) {
        var level = (ServerLevel) skate.level();
        var sand = level.getBlockState(BlockPos.containing(exit).below());
        double spread = Mth.lerp(progress, 0.50, 0.18);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand),
                exit.x, exit.y + 0.08, exit.z, 7, spread, 0.04, spread, 0.04);
        if (progress == 0.0F) {
            skate.playSound(SoundEvents.SAND_STEP, 1.0F, 0.65F);
        }
    }

    @Nullable
    Vec3 surface() {
        Level level = skate.level();
        BlockPos.MutableBlockPos pos = skate.blockPosition().mutable();
        for (int depth = 0; depth <= MAX_DEPTH; depth++, pos.move(0, 1, 0)) {
            if (!level.getBlockState(pos).is(ModTags.BURROWABLE_SAND)) {
                Vec3 exit = new Vec3(skate.getX(), pos.getY(), skate.getZ());
                if (isOpenAt(exit) && canPassTo(exit)) {
                    return exit;
                }
                return null;
            }
        }
        return null;
    }

    void swim(@Nullable Vec3 destination) {
        Vec3 position = skate.position();
        if (skate.tickCount >= turnAt) {
            float angle = skate.getRandom().nextFloat() * Mth.TWO_PI;
            heading = new Vec3(Mth.cos(angle), (skate.getRandom().nextDouble() - 0.5) * 0.7, Mth.sin(angle));
            turnAt = skate.tickCount + 32 + skate.getRandom().nextInt(48);
        }
        Vec3 direction = destination == null ? heading : destination.subtract(position).normalize();
        for (float angle : SWIM_TURNS) {
            Vec3 step = direction.yRot(angle).scale(SWIM_SPEED);
            if (tryStep(position, step) || tryStep(position, step.multiply(1.0, 0.0, 1.0))) {
                return;
            }
        }
        // A sand layer can end at a ledge. Try changing depth while retaining horizontal momentum.
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z).normalize().scale(SWIM_SPEED * 0.7);
        tryStep(position, horizontal.add(0.0, 0.10, 0.0));
        if (skate.position().equals(position)) {
            tryStep(position, horizontal.add(0.0, -0.10, 0.0));
        }
    }

    private boolean tryStep(Vec3 position, Vec3 step) {
        Vec3 next = position.add(step);
        if (step.lengthSqr() < 1.0E-6 || !canSwimAt(next)) {
            return false;
        }
        // Limit descents to the depth from which the skate can surface.
        BlockPos columnLimit = BlockPos.containing(next).above(MAX_DEPTH);
        if (skate.level().getBlockState(columnLimit).is(ModTags.BURROWABLE_SAND)) {
            return false;
        }
        skate.setPos(next);
        if (step.horizontalDistanceSqr() > 1.0E-6) {
            float yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG - 90.0);
            skate.setYRot(yaw);
            skate.setYHeadRot(yaw);
            skate.setYBodyRot(yaw);
        }
        return true;
    }

    private boolean canSwimAt(Vec3 position) {
        Level level = skate.level();
        AABB box = boxAt(position).deflate(1.0E-5);
        BlockPos bottom = BlockPos.containing(position);
        BlockPos top = BlockPos.containing(position.x, box.maxY, position.z);
        return level.hasChunkAt(bottom)
                && level.getBlockState(bottom).is(ModTags.BURROWABLE_SAND)
                && level.getBlockState(top).is(ModTags.BURROWABLE_SAND)
                && canOccupySandCorridor(box);
    }

    private boolean isOpenAt(Vec3 position) {
        Level level = skate.level();
        AABB box = boxAt(position);
        return level.getWorldBorder().isWithinBounds(box)
                && !level.getBlockCollisions(skate, box).iterator().hasNext()
                && !level.containsAnyLiquid(box);
    }

    private boolean canPassTo(Vec3 destination) {
        return canOccupySandCorridor(skate.getBoundingBox().minmax(boxAt(destination)).deflate(1.0E-5));
    }

    private boolean canOccupySandCorridor(AABB box) {
        Level level = skate.level();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (!level.hasChunksAt(min, max) || !level.getWorldBorder().isWithinBounds(box)) {
            return false;
        }
        // Check the full footprint before bypassing vanilla collision. Air at the edge of a sand bank is safe.
        for (BlockPos block : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(block);
            if (!state.getFluidState().isEmpty()
                    || (!state.is(ModTags.BURROWABLE_SAND) && !state.getCollisionShape(level, block).isEmpty())) {
                return false;
            }
        }
        return true;
    }

    private AABB boxAt(Vec3 position) {
        return skate.getBoundingBox().move(position.subtract(skate.position()));
    }

    void showTrail() {
        if (skate.burrowPhase() != BurrowPhase.BURIED) {
            lastTrailPosition = null;
            return;
        }
        Vec3 current = skate.position();
        Vec3 previous = lastTrailPosition;
        lastTrailPosition = current;
        if (previous == null) {
            return;
        }
        Vec3 motion = current.subtract(previous).multiply(1.0, 0.0, 1.0);
        if (motion.horizontalDistanceSqr() < 0.01 || motion.horizontalDistanceSqr() > 2.25) {
            return;
        }
        Vec3 exit = surface();
        if (exit == null || exit.y - skate.getY() > 1.25) {
            return;
        }
        Vec3 forward = motion.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        trailParticle(exit.add(forward.scale(0.22)), forward.scale(0.03), 0.10);
        Vec3 wake = exit.subtract(forward.scale(0.28));
        trailParticle(wake.add(side.scale(0.20)), side.scale(0.035), 0.06);
        trailParticle(wake.subtract(side.scale(0.20)), side.scale(-0.035), 0.06);
    }

    private void trailParticle(Vec3 point, Vec3 drift, double rise) {
        Level level = skate.level();
        BlockPos air = BlockPos.containing(point);
        var sand = level.getBlockState(air.below());
        if (sand.is(ModTags.BURROWABLE_SAND) && level.getBlockState(air).getCollisionShape(level, air).isEmpty()) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, sand), point.x, point.y + 0.05, point.z,
                    drift.x, rise, drift.z);
        }
    }
}

package yagen.waitmydawn.yagens_mobs.kavat;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class KavatHabits {
    enum Pose {
        NONE, WARM, SOCIAL_ROLL, BACKFLIP, SIDE, FIRE_ROLL, PADDLE, SWIM;
        static final Pose[] VALUES = values();
    }

    // The displacement is measured at unit entity scale. Decreasing positions roll back over the same flank.
    private static final double[][] SOCIAL_TRAVEL = {
        {0,0}, {10,.02}, {21,.34}, {32,.72}, {40,.93}, {52,.52}, {60,.69}, {70,.23}, {76,.08}
    };
    private static final double[][] FIRE_TRAVEL = {{0,0},{4,.035},{10,.32},{20,.84},{26,1.13},{31,1.20}};

    private static double offset(double[][] marks, int tick) {
        for (int i = 1; i < marks.length; i++) {
            if (tick <= marks[i][0]) {
                double t = (tick - marks[i-1][0]) / (marks[i][0] - marks[i-1][0]);
                return Mth.lerp(t*t*(3-2*t), marks[i-1][1], marks[i][1]);
            }
        }
        return marks[marks.length-1][1];
    }
    private static boolean hasSupport(Kavat cat, net.minecraft.world.phys.AABB bounds, double depth) {
        var center = bounds.getCenter();
        var feet = new net.minecraft.world.phys.AABB(center.x-.09, bounds.minY-depth, center.z-.09,
                center.x+.09, bounds.minY+.005, center.z+.09);
        return cat.level().getBlockCollisions(cat, feet).iterator().hasNext();
    }
    private static boolean grounded(Kavat cat) {
        // Vanilla travel may clear onGround on alternate ticks after a scripted horizontal move.
        return cat.onGround() || cat.getDeltaMovement().y <= .02 && hasSupport(cat, cat.getBoundingBox(), .045);
    }
    private static void roll(Kavat cat, float yaw, int elapsed, double[][] marks) {
        double step = (offset(marks, elapsed) - offset(marks, elapsed-1)) * cat.getScale();
        if (Math.abs(step) < .00001 || !grounded(cat)) return;
        double angle = Math.toRadians(yaw);
        var delta = new Vec3(Math.cos(angle)*step, 0, Math.sin(angle)*step);
        var next = cat.getBoundingBox().move(delta);
        if (cat.level().noCollision(cat, next) && hasSupport(cat, next, .23))
            // A downward contact component preserves onGround across horizontal self-moves.
            cat.move(net.minecraft.world.entity.MoverType.SELF, delta.add(0, -.04, 0));
    }

    static final class FireRoll extends Goal {
        private final Kavat cat;
        private int elapsed;
        private float heading;
        FireRoll(Kavat cat) {
            this.cat = cat;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }
        public boolean canUse() { return cat.isOnFire() && grounded(cat) && !cat.isInLava(); }
        public boolean canContinueToUse() { return elapsed < 31 && grounded(cat) && !cat.isInWater() && !cat.isInLava(); }
        public boolean requiresUpdateEveryTick() { return true; }
        public void start() {
            elapsed = 0;
            heading = cat.yBodyRot;
            cat.getNavigation().stop();
            cat.pose(Pose.FIRE_ROLL);
        }
        public void tick() {
            roll(cat, heading, ++elapsed, FIRE_TRAVEL);
            if (elapsed == 17) cat.clearFire();
        }
        public void stop() { cat.pose(Pose.NONE); }
    }

    static final class Water extends Goal {
        private final Kavat cat;
        Water(Kavat cat) {
            this.cat = cat;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }
        public boolean canUse() { return cat.isInWater(); }
        public boolean requiresUpdateEveryTick() { return true; }
        public void start() { cat.getNavigation().stop(); }
        public void tick() {
            var owner = cat.isOrderedToSit() ? null : cat.getOwner();
            boolean follow = owner != null && !owner.isSpectator() && owner.distanceToSqr(cat) < 32*32;
            boolean dive = follow && owner.isUnderWater() && cat.getAirSupply() > 60;
            var velocity = cat.getDeltaMovement();
            double dx = 0, dz = 0;
            if (follow) {
                var offset = owner.position().subtract(cat.position()).multiply(1, 0, 1);
                double speed = Mth.clamp((offset.length() - 1.4) * .12, 0, .25);
                var heading = offset.normalize().scale(speed);
                dx = heading.x; dz = heading.z;
                if (speed > .01) {
                    cat.setYRot(Mth.rotateIfNecessary(cat.getYRot(), (float)Math.toDegrees(Math.atan2(-dx, dz)), 12));
                    cat.setYBodyRot(cat.getYRot());
                }
            }
            double lift = dive ? Mth.clamp((owner.getEyeY() - cat.getEyeY()) * .19, -.21, .19)
                               : cat.isEyeInFluid(FluidTags.WATER) ? .14 : .016;
            if (follow && cat.horizontalCollision && !owner.isInWater() && owner.getY() > cat.getY())
                lift = .32;
            cat.setDeltaMovement(Mth.lerp(.32, velocity.x, dx), Mth.lerp(.45, velocity.y, lift),
                    Mth.lerp(.32, velocity.z, dz));
            cat.pose(dive ? Pose.SWIM : Pose.PADDLE);
        }
        public void stop() { cat.pose(Pose.NONE); }
    }

    static final class Leisure extends Goal {
        private final Kavat cat;
        private int nextCheck, remaining, deadline;
        private float heading;
        private BlockPos heat, bed;
        private Pose activity;
        Leisure(Kavat cat) {
            this.cat = cat;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        private boolean idle() {
            return grounded(cat) && !cat.isInWater() && !cat.isOnFire() && cat.getTarget() == null
                    && !cat.isOrderedToSit() && !cat.isPanicking() && cat.hurtTime == 0;
        }
        private static boolean warm(BlockState state) {
            return CampfireBlock.isLitCampfire(state) || state.getBlock() instanceof AbstractFurnaceBlock
                    && state.getValue(AbstractFurnaceBlock.LIT);
        }
        private boolean findHeat() {
            var level = cat.level();
            for (var pos : BlockPos.withinManhattan(cat.blockPosition(), 4, 1, 4)) {
                if (!warm(level.getBlockState(pos))) continue;
                var here = cat.blockPosition();
                if (here.getY() == pos.getY() && here.distManhattan(pos) == 1 && cat.closerThanSpot(here)) {
                    heat = pos.immutable();
                    bed = here;
                    return true;
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    var spot = pos.relative(side);
                    if (!level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)
                            || !level.noCollision(cat, cat.getBoundingBox().move(Vec3.atBottomCenterOf(spot).subtract(cat.position()))))
                        continue;
                    if (!cat.closerThanSpot(spot) && !canReach(spot)) continue;
                    heat = pos.immutable();
                    bed = spot;
                    return true;
                }
            }
            return false;
        }
        private boolean canReach(BlockPos spot) {
            var path = cat.getNavigation().createPath(spot, 0);
            return path != null && path.canReach();
        }
        public boolean canUse() {
            if (cat.tickCount < nextCheck || !idle()) return false;
            var rng = cat.getRandom();
            nextCheck = cat.tickCount + 70 + rng.nextInt(71);
            var owner = cat.getOwner();
            boolean near = owner instanceof Player && !owner.isSpectator() && cat.distanceToSqr(owner) <= 16;
            if (owner != null && cat.distanceToSqr(owner) > 36) return false;
            if (near && rng.nextInt(160) == 0 && cat.level().getEntitiesOfClass(Player.class,
                    owner.getBoundingBox().inflate(5), p -> !p.isSpectator() && p != owner && p.distanceToSqr(owner) <= 25).size() <= 1
                    && cat.level().noCollision(cat, cat.getBoundingBox().expandTowards(0, 1.8 * cat.getScale(), 0))) {
                activity = Pose.BACKFLIP; remaining = 28;
            } else if (near && rng.nextInt(12) == 0) {
                activity = Pose.SOCIAL_ROLL; remaining = 76;
            } else if (rng.nextInt(3) == 0 && findHeat()) {
                activity = Pose.WARM; remaining = 130 + rng.nextInt(100);
            } else if (rng.nextInt(18) == 0) {
                activity = Pose.SIDE; remaining = 90 + rng.nextInt(70);
            } else return false;
            return true;
        }
        public void start() {
            deadline = cat.tickCount + 100;
            heading = cat.yBodyRot;
            cat.getNavigation().stop();
            if (activity == Pose.WARM && !cat.closerThanSpot(bed))
                cat.getNavigation().moveTo(bed.getX()+.5, bed.getY(), bed.getZ()+.5, .88);
            else cat.pose(activity);
        }
        public boolean canContinueToUse() {
            if (remaining <= 0 || !idle()) return false;
            if (cat.getOwner() != null && cat.distanceToSqr(cat.getOwner()) > 36) return false;
            return activity != Pose.WARM || (warm(cat.level().getBlockState(heat))
                    && (cat.closerThanSpot(bed) || cat.tickCount < deadline && !cat.getNavigation().isDone()));
        }
        public boolean requiresUpdateEveryTick() { return true; }
        public void tick() {
            if (activity == Pose.WARM && !cat.closerThanSpot(bed)) return;
            cat.getNavigation().stop();
            cat.pose(activity);
            if (activity == Pose.SOCIAL_ROLL)
                roll(cat, heading, 77 - remaining, SOCIAL_TRAVEL);
            remaining--;
        }
        public void stop() {
            cat.pose(Pose.NONE);
            cat.getNavigation().stop();
            heat = bed = null;
        }
    }
    private KavatHabits() {}
}

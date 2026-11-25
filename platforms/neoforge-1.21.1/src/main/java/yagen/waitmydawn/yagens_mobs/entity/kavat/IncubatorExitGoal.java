package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class IncubatorExitGoal extends Goal {
    private final Kavat cat;
    private Incubator chamber;
    private Vec3 center, exit;
    private Direction opening;
    private boolean aligned;
    private int ticks;

    IncubatorExitGoal(Kavat cat) {
        this.cat = cat;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        if (cat.isOrderedToSit()) return false;
        var pos = cat.blockPosition();
        var level = cat.level();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IncubatorBlock)
                || !(level.getBlockEntity(IncubatorBlock.root(pos, state)) instanceof Incubator machine)
                || machine.isPacked() || machine.busy() || machine.shutterTravel(0) > 0) return false;
        var root = machine.getBlockPos();
        center = new Vec3(root.getX() + 1, root.getY() + IncubatorBlock.FLOOR, root.getZ() + 1);
        var target = cat.getTarget();
        var path = cat.getNavigation().getTargetPos();
        var owner = cat.getOwner();
        Vec3 wanted;
        if (target != null) wanted = target.position();
        else if (path != null && !cat.getNavigation().isDone()) wanted = path.getCenter();
        else if (owner != null && cat.distanceToSqr(owner) >= 36) wanted = owner.position();
        else return false;
        if (Math.abs(wanted.x - center.x) <= 1 && Math.abs(wanted.z - center.z) <= 1) return false;

        double best = Double.POSITIVE_INFINITY;
        opening = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 end = center.add(side.getStepX() * 1.85, 0, side.getStepZ() * 1.85);
            double score = end.distanceToSqr(wanted);
            if (score >= best) continue;
            var support = BlockPos.containing(end.x, root.getY() - .01, end.z);
            if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                    || WalkNodeEvaluator.getPathTypeStatic(cat, support.above()) != PathType.WALKABLE) continue;
            // Vanilla paths use half-block node centers. These doors straddle the full-block seam.
            double radius = cat.getBbWidth() * .5;
            double y = root.getY() + .275;
            AABB corridor = new AABB(center.x - radius, y, center.z - radius,
                    center.x + radius, y + cat.getBbHeight(), center.z + radius)
                    .expandTowards(end.x - center.x, 0, end.z - center.z);
            if (level.getBlockCollisions(cat, corridor).iterator().hasNext() || level.containsAnyLiquid(corridor)) continue;
            best = score;
            opening = side;
            exit = end;
        }
        chamber = machine;
        return opening != null;
    }

    @Override public void start() {
        aligned = false;
        ticks = 0;
        cat.getNavigation().stop();
    }

    @Override public boolean canContinueToUse() {
        return ticks < 55 && !cat.isOrderedToSit() && !chamber.isRemoved() && !chamber.busy()
                && cat.position().distanceToSqr(exit) > .025;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        ticks++;
        boolean eastWest = opening.getAxis() == Direction.Axis.X;
        double lateral = eastWest ? cat.getZ() - center.z : cat.getX() - center.x;
        if (Math.abs(lateral) < .10) aligned = true;
        cat.getNavigation().stop();
        Vec3 point = aligned ? exit : center;
        cat.getMoveControl().setWantedPosition(point.x, cat.getY(), point.z, 1.05);
    }
}

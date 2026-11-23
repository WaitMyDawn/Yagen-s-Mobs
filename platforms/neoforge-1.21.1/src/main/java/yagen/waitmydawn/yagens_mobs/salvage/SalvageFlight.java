package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

final class SalvageFlight {
    private static final double MAX_INTERCEPT_SPEED = 8;
    private static final int LEAD_TICKS = 32;
    private static final int[] HEADINGS = {35, -35, 75, -75, 120, -120, 180};

    private SalvageFlight() {}

    static Vec3 steer(SalvageRay ray, Vec3 target, double speed, boolean carryingPlayer) {
        Vec3 delta = target.subtract(ray.position());
        Vec3 direct = delta.normalize();
        double lookAhead = Math.min(delta.length(), Math.max(2.5, speed));
        double step = Math.min(delta.length(), speed);
        if (clear(ray, direct.scale(lookAhead), carryingPlayer)) {
            return direct.scale(step);
        }
        for (double rise : new double[]{.9, -.7, 1.8}) {
            Vec3 candidate = direct.add(0, rise, 0).normalize();
            if (clear(ray, candidate.scale(lookAhead), carryingPlayer)) {
                return candidate.scale(step * .8);
            }
        }
        for (int degrees : HEADINGS) {
            Vec3 candidate = direct.yRot((float) Math.toRadians(degrees));
            if (clear(ray, candidate.scale(lookAhead), carryingPlayer)) {
                return candidate.scale(step * .7);
            }
        }
        return Vec3.ZERO;
    }

    static Vec3 intercept(SalvageRay ray, Entity target, Vec3 point, Vec3 motion, double cruise) {
        double gravity = target.isNoGravity() ? 0 : target.getGravity();
        double bottom = ray.level().getMinBuildHeight() - 62;
        double surface = ray.level() instanceof ServerLevel level ? surfaceBelow(level, target.position(), 192) : bottom;
        double floor = Math.max(bottom, surface) + point.y - target.getY();
        double fallTicks = Math.max(1, (point.y - floor) / Math.max(.1, -motion.y));
        double timeBudget = Math.clamp(fallTicks * .65, 1, 24);
        Vec3 beforeVoid = point.add(motion.scale(timeBudget));
        double needed = beforeVoid.distanceTo(ray.position()) / timeBudget;
        double speed = Math.min(MAX_INTERCEPT_SPEED, Math.max(cruise, Math.max(motion.length() + .75, needed)));
        // Pick the earliest point the ray can reach while the falling entity is still above the void.
        Vec3 aim = point;
        for (int tick = 1; tick <= LEAD_TICKS; tick++) {
            Vec3 next = aim.add(motion);
            if (next.y < floor) {
                break;
            }
            aim = next;
            motion = motion.add(0, -gravity, 0).multiply(.98, .98, .98);
            if (ray.position().distanceToSqr(aim) <= speed * speed * tick * tick) {
                break;
            }
        }
        return steer(ray, aim, speed, false);
    }

    static boolean sweptContact(Vec3 rayBefore, Vec3 rayNow, Vec3 targetBefore, Vec3 targetNow, double reach) {
        Vec3 separation = targetBefore.subtract(rayBefore);
        Vec3 relativeMotion = targetNow.subtract(rayNow).subtract(separation);
        double moving = relativeMotion.lengthSqr();
        double time = moving < 1.e-8 ? 0 : Math.clamp(-separation.dot(relativeMotion) / moving, 0, 1);
        return separation.add(relativeMotion.scale(time)).lengthSqr() <= reach * reach;
    }

    static Vec3 climbApproach(SalvageRay ray, double height, boolean carryingPlayer) {
        Vec3 rise = new Vec3(0, height - ray.getY(), 0);
        AABB hull = hull(ray, carryingPlayer);
        if (clear(ray, hull, rise)) {
            return ray.position().add(rise);
        }
        // Leave overhangs laterally before climbing. Checking only the current column strands rays under shelves.
        for (int distance = 3; distance <= 12; distance += 3) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) {
                        continue;
                    }
                    Vec3 side = new Vec3(x * distance, 0, z * distance);
                    if (clear(ray, hull, side) && clear(ray, hull.move(side), rise)) {
                        return ray.position().add(side);
                    }
                }
            }
        }
        return ray.position().add(rise);
    }

    static boolean clear(SalvageRay ray, Vec3 offset, boolean carryingPlayer) {
        return clear(ray, hull(ray, carryingPlayer), offset);
    }

    private static AABB hull(SalvageRay ray, boolean carryingPlayer) {
        AABB box = ray.getBoundingBox().deflate(.03);
        return carryingPlayer ? box.expandTowards(0, -SalvageRay.RIDER_DROP, 0) : box;
    }

    private static boolean clear(SalvageRay ray, AABB hull, Vec3 offset) {
        AABB swept = hull.expandTowards(offset);
        // The loaded column can still be traversed below build height until the void kills the entity.
        return ray.level().hasChunksAt(Mth.floor(swept.minX), Mth.floor(swept.minZ), Mth.floor(swept.maxX), Mth.floor(swept.maxZ))
                && ray.level().getWorldBorder().isWithinBounds(swept) && ray.level().noCollision(ray, swept);
    }

    static boolean overVoid(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        int bottom = level.getMinBuildHeight();
        if (pos.getY() <= bottom || level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= bottom) {
            return true;
        }
        Vec3 end = new Vec3(point.x, bottom - .01, point.z);
        return level.clip(new ClipContext(point, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    static boolean clearBelow(ServerLevel level, Vec3 point, double depth) {
        return surfaceBelow(level, point, depth) == Double.NEGATIVE_INFINITY;
    }

    private static double surfaceBelow(ServerLevel level, Vec3 point, double depth) {
        if (point.y <= level.getMinBuildHeight()) {
            return Double.NEGATIVE_INFINITY;
        }
        var hit = level.clip(new ClipContext(point, new Vec3(point.x, Math.max(level.getMinBuildHeight() - .01, point.y - depth), point.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.NEGATIVE_INFINITY;
    }

    static @Nullable Vec3 landing(ServerLevel level, BlockPos around) {
        return BlockPos.betweenClosedStream(around.offset(-5, -2, -5), around.offset(5, 1, 5))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                .map(Vec3::atBottomCenterOf)
                .filter(pos -> level.getWorldBorder().isWithinBounds(BlockPos.containing(pos))
                        && level.noCollision(new AABB(pos.x - .42, pos.y + .02, pos.z - .42, pos.x + .42, pos.y + 3.5, pos.z + .42)))
                .min(Comparator.comparingDouble(pos -> pos.distanceToSqr(Vec3.atCenterOf(around)))).orElse(null);
    }

    static boolean lineOfSight(SalvageRay ray, Vec3 target) {
        return ray.level().clip(new ClipContext(ray.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ray)).getType() == HitResult.Type.MISS;
    }
}

package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;

final class CargoDetour {
    static final int SEARCH_RADIUS = 16;
    private static final int MAX_NODES = 2_400;
    private static final double BLOCK_BOTTOM = .98;
    private static final double CHEST_BOTTOM = 14.0 / 16.0;
    private static final double MAIL_BOTTOM = .375;
    private static final double REJOIN_FORWARD = 8;

    private CargoDetour() {}

    static AABB footprint(List<Vulture> birds) {
        AABB area = birds.getFirst().getBoundingBox();
        for (var bird : birds) {
            AABB body = bird.getBoundingBox();
            area = area.minmax(body);
            if (bird.isCarryingMail()) {
                area = area.minmax(body.expandTowards(0, -MAIL_BOTTOM, 0));
            }
            if (bird.carriedBlock().isAir()) {
                continue;
            }
            switch (bird.cargoDisplay()) {
                case SINGLE -> area = area.minmax(blockBox(bird.position(), .5, BLOCK_BOTTOM));
                case DOUBLE_CHEST -> {
                    double yaw = bird.getYRot() * Mth.DEG_TO_RAD;
                    Vec3 partner = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                    double extent = (Math.abs(partner.x) + Math.abs(partner.z)) * .5;
                    area = area.minmax(blockBox(bird.position(), extent, CHEST_BOTTOM))
                            .minmax(blockBox(bird.position().add(partner), extent, CHEST_BOTTOM));
                }
                case PARTNER -> {
                    // Both halves are rendered by the bird displaying the double chest.
                }
            }
        }
        return area;
    }

    private static AABB blockBox(Vec3 feet, double extent, double bottom) {
        return new AABB(feet.x - extent, feet.y - bottom, feet.z - extent,
                feet.x + extent, feet.y + 1 - bottom, feet.z + extent);
    }

    static boolean clear(ServerLevel level, Vulture bird, AABB footprint, Vec3 step) {
        AABB endpoint = footprint.move(step);
        AABB swept = footprint.minmax(endpoint);
        if (!FlightPath.loaded(level, swept) || level.containsAnyLiquid(swept)) {
            return false;
        }
        for (var shape : level.getBlockCollisions(bird, swept)) {
            for (AABB obstacle : shape.toAabbs()) {
                // Normal flight can touch an obstacle with its load before the carriers themselves stop.
                // Allow a detour to retreat from that contact without entering any new collision volume.
                if (obstacle.intersects(swept) && (!obstacle.intersects(footprint)
                        || overlap(endpoint, obstacle) > overlap(footprint, obstacle) + 1.0e-7)) {
                    return false;
                }
            }
        }
        return true;
    }

    static @Nullable List<Vec3> find(ServerLevel level, List<Vulture> birds, Vec3 origin, Vec3 finish, AABB footprint) {
        var leader = birds.getFirst();
        if (!FlightPath.loaded(level, footprint) || level.containsAnyLiquid(footprint)) {
            return null;
        }
        Vec3 heading = finish.subtract(origin).multiply(1, 0, 1);
        double distance = heading.horizontalDistance();
        boolean docking = distance <= 12;
        Vec3 forward = heading.normalize();
        var start = BlockPos.containing(origin);
        int area = Math.max(1, Mth.ceil(footprint.getXsize() * footprint.getZsize()));
        // A wider formation makes each collision probe more expensive.
        int budget = Math.clamp(16_000 / area, 24, MAX_NODES);
        var route = FlightPath.find(start, SEARCH_RADIUS, budget, pos -> {
            Vec3 offset = offset(start, pos);
            Vec3 location = origin.add(offset);
            if (docking) {
                return location.distanceToSqr(finish) < 2.25
                        && FlightPath.clear(level, leader, footprint.move(offset).minmax(footprint.move(finish.subtract(origin))));
            }
            Vec3 rejoin = finish.subtract(location).multiply(1, 0, 1).normalize().scale(3);
            AABB merge = footprint.move(offset);
            return offset.dot(forward) >= REJOIN_FORWARD
                    && FlightPath.clear(level, leader, merge.minmax(merge.move(rejoin)));
        }, pos -> {
            Vec3 offset = offset(start, pos);
            return docking ? origin.add(offset).distanceTo(finish) : Math.max(0, REJOIN_FORWARD - offset.dot(forward));
        }, (from, to) -> {
            Vec3 toOffset = offset(start, to);
            AABB bounds = footprint.move(toOffset);
            if (bounds.minY < level.getMinY() || bounds.maxY > level.getMaxY() + 1 + 96
                    || !level.getWorldBorder().isWithinBounds(bounds)) {
                return false;
            }
            return clear(level, leader, footprint.move(offset(start, from)), toOffset.subtract(offset(start, from)));
        });
        if (route == null) {
            return null;
        }
        var waypoints = new ArrayList<Vec3>(route.size());
        for (int i = 1; i < route.size(); i++) {
            waypoints.add(origin.add(offset(start, route.get(i))));
        }
        if (docking) {
            waypoints.add(finish);
        }
        return waypoints;
    }

    private static Vec3 offset(BlockPos start, BlockPos pos) {
        return new Vec3(pos.getX() - start.getX(), pos.getY() - start.getY(), pos.getZ() - start.getZ());
    }

    private static double overlap(AABB a, AABB b) {
        double x = Math.max(0, Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX));
        double y = Math.max(0, Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY));
        double z = Math.max(0, Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ));
        return x * y * z;
    }
}

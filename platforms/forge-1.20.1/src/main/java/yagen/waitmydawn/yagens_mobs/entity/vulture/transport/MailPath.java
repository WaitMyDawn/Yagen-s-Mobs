package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;

final class MailPath {
    private static final int MAX_NODES = 12_000;
    private static final int RADIUS = 80;
    private static final double BAG_CLEARANCE = .375;

    private MailPath() {}

    static Vec3 point(BlockPos cell) {
        return Vec3.atBottomCenterOf(cell).add(0, BAG_CLEARANCE, 0);
    }

    static boolean clear(ServerLevel level, Vulture bird, Vec3 from, Vec3 to) {
        AABB start = bird.getDimensions(bird.getPose()).makeBoundingBox(from);
        AABB volume = start.minmax(start.move(to.subtract(from))).expandTowards(0, -BAG_CLEARANCE, 0);
        return FlightPath.clear(level, bird, volume);
    }

    static @Nullable List<BlockPos> find(ServerLevel level, Vulture bird, BlockPos start, @Nullable BlockPos target) {
        if (!clear(level, bird, point(start), point(start))) {
            return null;
        }
        return FlightPath.find(start, RADIUS, MAX_NODES, pos -> target == null ? sky(level, pos) : pos.equals(target),
                pos -> estimate(level, pos, target), (from, to) -> !level.isOutsideBuildHeight(to.above())
                        && !level.isOutsideBuildHeight(to) && level.getWorldBorder().isWithinBounds(to)
                        && clear(level, bird, point(from), point(to)));
    }

    static boolean sky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    static @Nullable List<BlockPos> toStation(ServerLevel level, Vulture bird, BlockPos from,
                                              BlockPos column, Direction.Axis axis) {
        if (Math.abs(from.getX() - column.getX()) + Math.abs(from.getZ() - column.getZ()) >= RADIUS) {
            return null;
        }
        BlockPos dock = dock(level, bird, column, axis);
        return dock == null ? null : find(level, bird, from, dock);
    }

    static @Nullable BlockPos dock(ServerLevel level, Vulture bird, BlockPos column, Direction.Axis axis) {
        Direction side = axis == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
        for (BlockPos pos : List.of(column.above(2), column.relative(side).above(),
                column.relative(side.getOpposite()).above(), column.relative(side), column.relative(side.getOpposite()))) {
            if (!level.isOutsideBuildHeight(pos.above()) && clear(level, bird, point(pos), point(pos))) {
                return pos;
            }
        }
        return null;
    }

    private static double estimate(ServerLevel level, BlockPos pos, @Nullable BlockPos target) {
        if (target != null) {
            return pos.distManhattan(target);
        }
        // An exit can lie below the current node. Surface height prioritizes ascent without pruning descents.
        return 1.25 * Math.max(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - pos.getY());
    }

}

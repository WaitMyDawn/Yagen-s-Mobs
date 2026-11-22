package yagen.waitmydawn.yagens_mobs.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

final class RockHangerSurfaces {
    private static final int MAX_ROUTE_NODES = 2048;
    private static final int FALLBACK_LOOKAHEAD = 144;
    private static final int SPAWN_NEIGHBOR_RADIUS = 2;
    private static final int MAX_MOUNTS = 32;
    private static final List<BlockPos> NEARBY_STEPS = climbOffsets();
    private static final int MOUNT_SEARCH_RADIUS = 12;
    private static final Direction[] FACES = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    record Step(BlockPos cell, Direction support, Vec3 feet) {
        BlockPos approach() {
            return cell.relative(support.getOpposite());
        }

        BlockPos block() {
            return cell.relative(support);
        }
    }

    record Route(List<Step> steps, BlockPos anchor) {
    }

    private record MountArea(int x, int z, Direction support) {
    }

    private record Link(Step parent, @Nullable Vec3 bend) {
    }

    private record Visit(Step step, double cost) {
    }

    private RockHangerSurfaces() {
    }

    @Nullable
    static BlockPos findSpawnAnchor(LevelReader level, BlockPos attempt, EntityType<?> type) {
        if (!level.hasChunkAt(attempt) || level.canSeeSky(attempt)) {
            return null;
        }
        BlockPos overhead = overheadAnchor(level, attempt, type);
        if (overhead != null) {
            return overhead;
        }

        var biome = level.getBiome(attempt);
        int maxLight = level.dimensionType().monsterSpawnBlockLightLimit();
        Vec3 feet = Vec3.atBottomCenterOf(attempt);
        // A nearby column is eligible only when it belongs to the same chamber and biome as the spawn attempt.
        for (BlockPos column : BlockPos.withinManhattan(attempt, SPAWN_NEIGHBOR_RADIUS, 0, SPAWN_NEIGHBOR_RADIUS)) {
            if (column.equals(attempt) || !level.hasChunkAt(column) || !level.getBiome(column).equals(biome)
                    || level.getBrightness(LightLayer.BLOCK, column) > maxLight
                    || !sweepClear(level, type, feet, Vec3.atBottomCenterOf(column))) {
                continue;
            }
            BlockPos nearby = overheadAnchor(level, column, type);
            if (nearby != null) {
                return nearby;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos overheadAnchor(LevelReader level, BlockPos column, EntityType<?> type) {
        if (!level.hasChunkAt(column) || level.canSeeSky(column)) {
            return null;
        }
        var cursor = column.mutable();
        for (int y = column.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            cursor.setY(y);
            var state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty() || !state.getFluidState().isEmpty()) {
                return canHang(level, cursor, type) ? cursor.immutable() : null;
            }
        }
        return null;
    }

    static boolean sturdy(LevelReader level, BlockPos block, Direction towardBlock) {
        return !level.isOutsideBuildHeight(block) && level.hasChunkAt(block)
                && level.getBlockState(block).isFaceSturdy(level, block, towardBlock.getOpposite());
    }

    static Vec3 ceilingFeet(BlockPos anchor, EntityType<?> type) {
        return new Vec3(anchor.getX() + 0.5, anchor.getY() - type.getHeight() - 0.14, anchor.getZ() + 0.5);
    }

    static Vec3 idleFeet(BlockPos anchor, EntityType<?> type, int length) {
        return ceilingFeet(anchor, type).add(0, -length + 0.14, 0);
    }

    static boolean canHang(LevelReader level, BlockPos anchor, EntityType<?> type) {
        return canHang(level, anchor, type, ServerConfigs.ROCK_HANGER_CEILING_CLEARANCE.get());
    }

    private static boolean canHang(LevelReader level, BlockPos anchor, EntityType<?> type, int clearance) {
        Vec3 top = ceilingFeet(anchor, type);
        Vec3 bottom = new Vec3(top.x, anchor.getY() - clearance, top.z);
        if (!sturdy(level, anchor, Direction.UP) || !clear(level, type, top, bottom)) {
            return false;
        }
        for (int y = BlockPos.containing(bottom).getY(); y < anchor.getY(); y++) {
            if (!level.getFluidState(anchor.atY(y)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean canHangFromEgg(LevelReader level, BlockPos anchor, EntityType<?> type) {
        int clearance = ServerConfigs.ROCK_HANGER_CEILING_CLEARANCE.get();
        if (!canHang(level, anchor, type, clearance)) {
            return false;
        }
        for (int step = 1; step <= clearance; step++) {
            if (!level.getBlockState(anchor.below(step)).isAir()) {
                return false;
            }
        }
        return true;
    }

    static boolean clear(LevelReader level, EntityType<?> type, Vec3 from, Vec3 to) {
        AABB box = type.getSpawnAABB(from.x, from.y, from.z).expandTowards(to.subtract(from));
        return clear(level, box);
    }

    static boolean clear(LevelReader level, AABB box) {
        return loaded(level, box) && !level.getBlockCollisions(null, box).iterator().hasNext();
    }

    static boolean sweepClear(LevelReader level, EntityType<?> type, Vec3 from, Vec3 to) {
        AABB body = type.getSpawnAABB(from.x, from.y, from.z);
        Vec3 delta = to.subtract(from);
        AABB sweep = body.expandTowards(delta);
        if (!loaded(level, sweep)) {
            return false;
        }
        Vec3 center = body.getCenter();
        Vec3 end = center.add(delta);
        double halfWidth = body.getXsize() / 2;
        double halfHeight = body.getYsize() / 2;
        // A diagonal sweep visits only the volumes hit by the moving body, including when rounding a ledge.
        for (var shape : level.getBlockCollisions(null, sweep)) {
            for (AABB obstacle : shape.toAabbs()) {
                AABB expanded = obstacle.inflate(halfWidth, halfHeight, halfWidth).deflate(1e-6);
                if (expanded.contains(center) || expanded.contains(end) || expanded.clip(center, end).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean tetherClear(LevelReader level, Vec3 anchor, Vec3 attachment) {
        Vec3 start = anchor.add(0, -0.035, 0);
        return loaded(level, new AABB(start, attachment))
                && level.clip(new ClipContext(start, attachment, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private static boolean loaded(LevelReader level, AABB box) {
        return box.minY >= level.getMinBuildHeight() && box.maxY <= level.getMaxBuildHeight()
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }

    @Nullable
    static Route findRoute(RockHanger mob) {
        var level = mob.level();
        BlockPos origin = mob.blockPosition();
        BlockPos overhead = overheadAnchor(level, origin, mob.getType());
        var mounts = new ArrayList<Step>();
        Map<BlockPos, List<Step>> cache = new HashMap<>();
        int radius = MOUNT_SEARCH_RADIUS;
        for (BlockPos cell : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 3, radius))) {
            if (!sturdy(level, cell.below(), Direction.DOWN)) {
                continue;
            }
            for (Step step : steps(mob, cell, cache)) {
                if (step.support().getAxis().isHorizontal()) {
                    mounts.add(step);
                }
            }
        }
        mounts.sort(Comparator.comparingDouble(step -> step.feet().distanceToSqr(mob.position())));

        var queue = new PriorityQueue<Visit>(Comparator.comparingDouble(visit -> visit.cost() + roofBias(visit.step(), overhead)));
        Map<Step, Link> parent = new HashMap<>();
        Map<Step, Double> costs = new HashMap<>();
        Set<Step> visited = new HashSet<>();
        Set<MountArea> approaches = new HashSet<>();
        int attempts = 0;
        for (Step mount : mounts) {
            var area = new MountArea(Math.floorDiv(mount.cell().getX(), 3),
                    Math.floorDiv(mount.cell().getZ(), 3), mount.support());
            if (approaches.contains(area)) {
                continue;
            }
            if (attempts++ >= MAX_MOUNTS) {
                break;
            }
            boolean nearby = approachable(mob, mount);
            var path = nearby ? null : approachPath(mob, mount);
            if (nearby || path != null && path.canReach()) {
                double cost = Math.sqrt(mount.feet().distanceToSqr(mob.position())) * 0.45;
                costs.put(mount, cost);
                queue.add(new Visit(mount, cost));
                approaches.add(area);
                if (queue.size() == 8) {
                    break;
                }
            }
        }

        Step fallback = null;
        double fallbackScore = Double.POSITIVE_INFINITY;
        int searchLimit = MAX_ROUTE_NODES;
        while (!queue.isEmpty() && visited.size() < searchLimit) {
            Visit visit = queue.remove();
            Step current = visit.step();
            if (!visited.add(current)) {
                continue;
            }
            if (current.support() == Direction.UP && canHang(level, current.block(), mob.getType())
                    && clear(level, mob.getType(), current.feet(), ceilingFeet(current.block(), mob.getType()))) {
                if (current.block().equals(overhead)) {
                    return buildRoute(current, parent, mob.getType());
                }
                if (fallback == null && overhead == null) {
                    searchLimit = Math.min(searchLimit, visited.size() + FALLBACK_LOOKAHEAD);
                }
                double score = fallbackScore(mob, origin, visit);
                if (score < fallbackScore) {
                    fallback = current;
                    fallbackScore = score;
                }
            }
            enqueue(mob, visit, current.cell(), cache, queue, costs, parent);
            for (BlockPos offset : NEARBY_STEPS) {
                enqueue(mob, visit, current.cell().offset(offset), cache, queue, costs, parent);
            }
        }
        return fallback == null ? null : buildRoute(fallback, parent, mob.getType());
    }

    private static double roofBias(Step step, @Nullable BlockPos roof) {
        if (roof == null) {
            return 0;
        }
        BlockPos cell = step.cell();
        double across = Math.abs(cell.getX() - roof.getX()) + Math.abs(cell.getZ() - roof.getZ());
        return across * 0.5 + Math.abs(cell.getY() - (roof.getY() - 1)) * 0.13;
    }

    private static double fallbackScore(RockHanger mob, BlockPos origin, Visit visit) {
        BlockPos roof = visit.step().block();
        var type = mob.getType();
        var level = mob.level();
        Vec3 upper = ceilingFeet(roof, type);
        Vec3 lower = idleFeet(roof, type, 6);
        int openSides = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 offset = Vec3.atLowerCornerOf(side.getNormal());
            if (clear(level, type, upper.add(offset), lower.add(offset))) {
                openSides++;
            }
        }
        // The route still has to reach the anchor. Among reachable spots prefer a ceiling with room on the sides.
        double distance = Math.hypot(roof.getX() - origin.getX(), roof.getZ() - origin.getZ());
        return distance * 0.65 + visit.cost() * 0.08 - openSides * 1.8 + mob.getRandom().nextDouble() * 2.5;
    }

    private static Route buildRoute(Step destination, Map<Step, Link> parent, EntityType<?> type) {
        var route = new ArrayList<Step>();
        for (Step step = destination; step != null;) {
            route.add(step);
            Link edge = parent.get(step);
            if (edge != null && edge.bend() != null) {
                Step previous = edge.parent();
                route.add(new Step(previous.cell(), previous.support(), edge.bend()));
            }
            step = edge != null ? edge.parent() : null;
        }
        Collections.reverse(route);
        BlockPos roof = destination.block();
        route.add(new Step(destination.cell(), Direction.UP, ceilingFeet(roof, type)));
        return new Route(route, roof);
    }

    static boolean approachable(RockHanger mob, Step mount) {
        return mob.position().distanceToSqr(mount.feet()) <= 3.24 && Math.abs(mob.getY() - mount.feet().y) <= 0.25
                && clear(mob.level(), mob.getType(), mob.position(), mount.feet());
    }

    static Path approachPath(RockHanger mob, Step mount) {
        var navigation = mob.getNavigation();
        var direct = navigation.createPath(mount.cell(), 0);
        return direct != null && direct.canReach() ? direct : navigation.createPath(mount.approach(), 0);
    }

    private static void enqueue(RockHanger mob, Visit current, BlockPos cell, Map<BlockPos, List<Step>> cache,
                                PriorityQueue<Visit> queue, Map<Step, Double> costs, Map<Step, Link> parent) {
        Step from = current.step();
        boolean turn = from.cell().equals(cell);
        double cost = current.cost() + (turn ? 0.35
                : 1 + (cell.distManhattan(from.cell()) - 1) * 0.45 - 0.85 * (cell.getY() - from.cell().getY()));
        for (Step to : steps(mob, cell, cache)) {
            // Edge-sharing supports permit the hooks to transfer onto a ledge or stepped face.
            double previousCost = costs.getOrDefault(to, Double.POSITIVE_INFINITY);
            if (from.block().distManhattan(to.block()) > 2 || cost >= previousCost) {
                continue;
            }
            var level = mob.level();
            var type = mob.getType();
            if (sweepClear(level, type, from.feet(), to.feet())) {
                costs.put(to, cost);
                parent.put(to, new Link(from, null));
                queue.add(new Visit(to, cost));
            } else if (cost + 0.6 < previousCost) {
                Vec3 bend = ledgeBend(level, type, from.feet(), to.feet());
                if (bend != null) {
                    costs.put(to, cost + 0.6);
                    parent.put(to, new Link(from, bend));
                    queue.add(new Visit(to, cost + 0.6));
                }
            }
        }
    }

    @Nullable
    private static Vec3 ledgeBend(LevelReader level, EntityType<?> type, Vec3 from, Vec3 to) {
        if (Math.abs(from.y - to.y) < 0.05 || from.subtract(to).horizontalDistanceSqr() < 0.01) {
            return null;
        }
        Vec3 riseFirst = new Vec3(from.x, to.y, from.z);
        if (sweepClear(level, type, from, riseFirst) && sweepClear(level, type, riseFirst, to)) {
            return riseFirst;
        }
        Vec3 crossFirst = new Vec3(to.x, from.y, to.z);
        return sweepClear(level, type, from, crossFirst) && sweepClear(level, type, crossFirst, to) ? crossFirst : null;
    }

    private static List<BlockPos> climbOffsets() {
        var result = new ArrayList<BlockPos>(18);
        for (BlockPos candidate : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
            int distance = candidate.distManhattan(BlockPos.ZERO);
            if (distance == 1 || distance == 2) {
                result.add(candidate.immutable());
            }
        }
        return List.copyOf(result);
    }

    private static List<Step> steps(RockHanger mob, BlockPos cell, Map<BlockPos, List<Step>> cache) {
        return cache.computeIfAbsent(cell.immutable(), pos -> {
            var level = mob.level();
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)
                    || !level.getFluidState(pos).isEmpty()) {
                return List.of();
            }
            var result = new ArrayList<Step>(FACES.length);
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            double offset = Math.max(0, mob.getBbWidth() / 2.0 - 0.45);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (sturdy(level, pos.relative(side), side)) {
                    x -= side.getStepX() * offset;
                    z -= side.getStepZ() * offset;
                }
            }
            for (Direction side : FACES) {
                if (!sturdy(level, pos.relative(side), side)) {
                    continue;
                }
                double y = pos.getY() + (side == Direction.UP ? 0.86 - mob.getBbHeight() : 0.08);
                Vec3 feet = new Vec3(x, y, z);
                if (clear(level, mob.getType(), feet, feet)) {
                    result.add(new Step(pos, side, feet));
                }
            }
            return result;
        });
    }
}


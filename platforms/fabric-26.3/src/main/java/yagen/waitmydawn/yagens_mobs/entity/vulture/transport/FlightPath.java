package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

final class FlightPath {
    private static final Direction[] STEPS = Direction.values();

    private FlightPath() {}

    static boolean clear(ServerLevel level, Entity bird, AABB volume) {
        return loaded(level, volume) && level.noBlockCollision(bird, volume) && !level.containsAnyLiquid(volume);
    }

    static boolean loaded(ServerLevel level, AABB volume) {
        int minX = Mth.floor(volume.minX) >> 4;
        int maxX = Mth.floor(volume.maxX) >> 4;
        int minZ = Mth.floor(volume.minZ) >> 4;
        int maxZ = Mth.floor(volume.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    static void addChunks(Set<ChunkPos> chunks, AABB volume) {
        for (int x = Mth.floor(volume.minX) >> 4; x <= Mth.floor(volume.maxX) >> 4; x++) {
            for (int z = Mth.floor(volume.minZ) >> 4; z <= Mth.floor(volume.maxZ) >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
    }

    static @Nullable List<BlockPos> find(BlockPos start, int radius, int budget, Predicate<BlockPos> goal,
                                        ToDoubleFunction<BlockPos> estimate, BiPredicate<BlockPos, BlockPos> traverse) {
        var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::score)
                .thenComparing(Comparator.comparingInt(Node::distance).reversed()));
        Map<BlockPos, Node> visited = new HashMap<>();
        Node first = new Node(start, null, 0, estimate.applyAsDouble(start));
        open.add(first);
        visited.put(start, first);
        for (int count = 0; count < budget && !open.isEmpty(); ) {
            Node current = open.remove();
            if (visited.get(current.pos()) != current) {
                continue;
            }
            count++;
            BlockPos pos = current.pos();
            if (goal.test(pos)) {
                var route = new ArrayList<BlockPos>();
                for (Node node = current; node != null; node = node.previous()) {
                    route.add(node.pos());
                }
                Collections.reverse(route);
                return route;
            }
            int distance = current.distance() + 1;
            for (Direction step : STEPS) {
                BlockPos next = pos.relative(step);
                Node previous = visited.get(next);
                if (previous != null && previous.distance() <= distance
                        || Math.abs(next.getX() - start.getX()) + Math.abs(next.getZ() - start.getZ()) > radius
                        || !traverse.test(pos, next)) {
                    continue;
                }
                Node candidate = new Node(next, current, distance, distance + estimate.applyAsDouble(next));
                visited.put(next, candidate);
                open.add(candidate);
            }
        }
        return null;
    }

    private record Node(BlockPos pos, @Nullable Node previous, int distance, double score) {}
}

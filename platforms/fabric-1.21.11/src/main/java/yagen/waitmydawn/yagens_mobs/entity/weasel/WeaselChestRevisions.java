package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class WeaselChestRevisions {
    private static final Map<LevelChunk, Long> REVISIONS = new WeakHashMap<>();
    private static long counter;

    private WeaselChestRevisions() {}

    public static void mark(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            REVISIONS.put(chunk, ++counter);
        }
    }

    static long since(ServerLevel level, BlockPos center, int radius) {
        long newest = 0;
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    newest = Math.max(newest, REVISIONS.getOrDefault(chunk, 0L));
                }
            }
        }
        return newest;
    }
}


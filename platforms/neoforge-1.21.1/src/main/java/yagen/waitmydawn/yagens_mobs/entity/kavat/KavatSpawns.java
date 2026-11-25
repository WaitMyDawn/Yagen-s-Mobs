package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

public final class KavatSpawns {
    public static boolean placement(
            EntityType<Kavat> type, ServerLevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource rng) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION)
            return true;
        var dimension = world.getLevel().dimension();
        return ServerConfigs.KAVAT.canSpawn()
                && (dimension == Level.OVERWORLD || dimension == Level.NETHER || dimension == Level.END)
                && Mob.checkMobSpawnRules(type, world, reason, pos, rng);
    }
    static int coat(ServerLevelAccessor world, BlockPos pos, RandomSource rng) {
        ServerLevel level = world.getLevel();
        var biome = world.getBiome(pos);
        if (level.dimension() == Level.END)
            return biome.is(Biomes.THE_END) ? 9 : 10;
        if (level.dimension() == Level.NETHER) {
            if (world instanceof ServerLevel live && isWitherSpawnSite(live, pos))
                return 8;
            if (biome.is(Biomes.BASALT_DELTAS))
                return 12;
            if (biome.is(Biomes.CRIMSON_FOREST) || biome.is(Biomes.WARPED_FOREST))
                return 13;
        }
        if (biome.is(Biomes.LUSH_CAVES))
            return 11;
        if (biome.value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW
                && pos.getY()
                        >= world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                   pos.getX(), pos.getZ())
                                - 1)
            return 7;
        if (level.dimension() == Level.OVERWORLD && pos.getY() >= world.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1) {
            if (biome.is(Biomes.CHERRY_GROVE))
                return 14;
            if (biome.is(Biomes.DESERT))
                return 15;
        }
        return rng.nextInt(7);
    }

    static boolean isWitherSpawnSite(ServerLevel level, BlockPos pos) {
        var structures = level.structureManager();
        boolean withers = NaturalSpawner.isInNetherFortressBounds(pos, level, MobCategory.MONSTER, structures)
                || level.getChunkSource().getGenerator().getMobsAt(level.getBiome(pos), structures, MobCategory.MONSTER, pos)
                        .unwrap().stream().anyMatch(entry -> entry.type == EntityType.WITHER_SKELETON);
        var bounds = EntityType.WITHER_SKELETON.getDimensions().makeBoundingBox(pos.getBottomCenter());
        return withers && !level.getBlockCollisions(null, bounds).iterator().hasNext();
    }
}

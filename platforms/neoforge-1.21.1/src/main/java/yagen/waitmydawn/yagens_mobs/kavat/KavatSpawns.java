package yagen.waitmydawn.yagens_mobs.kavat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class KavatSpawns {
    private static MobSpawnSettings.SpawnerData entry;
    public static void pool(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level))
            return;
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER && level.dimension() != Level.END)
            return;
        var rules = ServerConfigs.KAVAT;
        if (!rules.canSpawn())
            return;
        // NaturalSpawner re-fetches the pool and checks the selected entry by identity.
        if (entry == null || entry.getWeight().asInt() != rules.weight().get()
                || entry.minCount != rules.minGroupSize().get() || entry.maxCount != rules.maxGroupCount())
            entry = new MobSpawnSettings.SpawnerData(
                    ModEntities.KAVAT.get(), rules.weight().get(), rules.minGroupSize().get(), rules.maxGroupCount());
        event.addSpawnerData(entry);
    }
    public static boolean placement(
            EntityType<Kavat> type, ServerLevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource rng) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION)
            return true;
        return ServerConfigs.KAVAT.canSpawn() && world.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(world, pos, rng)
                && Mob.checkMobSpawnRules(type, world, reason, pos, rng);
    }
    static int coat(ServerLevelAccessor world, BlockPos pos, RandomSource rng) {
        ServerLevel level = world.getLevel();
        var biome = world.getBiome(pos);
        if (level.dimension() == Level.END)
            return biome.is(Biomes.THE_END) ? 9 : 10;
        if (level.dimension() == Level.NETHER) {
            if ((NaturalSpawner.isInNetherFortressBounds(pos, level, MobCategory.MONSTER, level.structureManager())
                        || level.getChunkSource()
                                .getGenerator()
                                .getMobsAt(biome, level.structureManager(), MobCategory.MONSTER, pos)
                                .unwrap()
                                .stream()
                                .anyMatch(entry -> entry.type == EntityType.WITHER_SKELETON))
                    && level.noCollision(
                            EntityType.WITHER_SKELETON.getDimensions().makeBoundingBox(pos.getBottomCenter())))
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
}

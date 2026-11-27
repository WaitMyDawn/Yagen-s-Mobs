package yagen.waitmydawn.yagens_mobs.spawn;

import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biomes;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SpawnDefaults;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class ConfiguredMobSpawns {
    private ConfiguredMobSpawns() {
    }

    public static void register() {
        add("collector", tagged("collector/habitats"), ModEntities.COLLECTOR.get(), ServerConfigs.COLLECTOR, 1);
        add("kavat", tagged("kavat_habitat").and(tagged("kavat_adjusted_habitat").negate()),
                ModEntities.KAVAT.get(), ServerConfigs.KAVAT, 1);
        add("kavat_reduced", tagged("kavat_reduced_habitat"), ModEntities.KAVAT.get(), ServerConfigs.KAVAT, .5);
        add("kavat_desert", c -> c.getBiomeKey().equals(Biomes.DESERT), ModEntities.KAVAT.get(), ServerConfigs.KAVAT, .3);
        add("kavat_cherry_grove", c -> c.getBiomeKey().equals(Biomes.CHERRY_GROVE), ModEntities.KAVAT.get(), ServerConfigs.KAVAT, .2);
        add("kavat_nether", c -> c.hasTag(BiomeTags.IS_NETHER), ModEntities.KAVAT.get(), ServerConfigs.KAVAT, 2);
        add("kavat_end", c -> c.hasTag(BiomeTags.IS_END), ModEntities.KAVAT.get(), ServerConfigs.KAVAT, .3);
        add("desert_skate", tagged("has_desert_skate"), ModEntities.DESERT_SKATE.get(), ServerConfigs.DESERT_SKATE, 1.5);
        add("condroc_savanna", tagged("condroc_savannas"), ModEntities.VULTURE.get(), ServerConfigs.VULTURE, 2);
        add("condroc_arid", tagged("condroc_arid"), ModEntities.VULTURE.get(), ServerConfigs.VULTURE, .4);
        add("condroc_ravines", overworld().and(tagged("condroc_ravine_exclusions").negate()),
                ModEntities.VULTURE.get(), ServerConfigs.VULTURE, .13);
        add("rock_hanger_dripstone", context -> context.getBiomeKey().equals(Biomes.DRIPSTONE_CAVES),
                ModEntities.ROCK_HANGER.get(), ServerConfigs.ROCK_HANGER, 1);
        add("rock_hanger_lush", tagged("rock_hanger_habitat")
                        .and(context -> !context.getBiomeKey().equals(Biomes.DRIPSTONE_CAVES)),
                ModEntities.ROCK_HANGER.get(), ServerConfigs.ROCK_HANGER, .3);
        add("rock_hanger_ordinary_caves", overworld().and(tagged("rock_hanger_ordinary_cave_exclusions").negate()),
                ModEntities.ROCK_HANGER.get(), ServerConfigs.ROCK_HANGER, 1);
        add("bubble_salamander_river", tagged("bubble_salamander_river"), ModEntities.BUBBLE_SALAMANDER.get(),
                ServerConfigs.BUBBLE_SALAMANDER, 1);
        add("bubble_salamander_ocean", tagged("bubble_salamander_ocean"), ModEntities.BUBBLE_SALAMANDER.get(),
                ServerConfigs.BUBBLE_SALAMANDER, .5);
        add("velocipod", tagged("velocipod_habitat"), ModEntities.VELOCIPOD.get(), ServerConfigs.VELOCIPOD, 1);
        add("lumen_butterfly", tagged("lumen_butterfly_habitat"), LumenButterflies.TYPE.get(),
                ServerConfigs.LUMEN_BUTTERFLY, 1);
        add("snowcache_weasel", tagged("snowcache_weasel_spawns"),
                yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels.TYPE.get(), ServerConfigs.SNOWCACHE_WEASEL, 1);
    }

    private static Predicate<BiomeSelectionContext> tagged(String name) {
        var tag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(ModIdentity.ID, name));
        return context -> context.hasTag(tag);
    }

    private static Predicate<BiomeSelectionContext> overworld() {
        return context -> context.hasTag(BiomeTags.IS_OVERWORLD);
    }

    private static void add(String path, Predicate<BiomeSelectionContext> selector, EntityType<?> entity,
                            ServerConfigs.Spawns config, double factor) {
        var modifier = BiomeModifications.create(Identifier.fromNamespaceAndPath(ModIdentity.ID, path));
        modifier.add(ModificationPhase.ADDITIONS, selector, context -> {
            if (config.canSpawn()) {
                context.getSpawnSettings().addSpawn(entity.getCategory(), new MobSpawnSettings.SpawnerData(entity, config.minGroupSize().get(), config.maxGroupCount()),
                        SpawnDefaults.scaledWeight(config.weight().get(), factor));
            }
        });
    }
}

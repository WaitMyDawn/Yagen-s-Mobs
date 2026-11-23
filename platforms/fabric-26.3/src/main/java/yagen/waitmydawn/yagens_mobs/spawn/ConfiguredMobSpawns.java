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
import yagen.waitmydawn.yagens_mobs.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SpawnDefaults;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class ConfiguredMobSpawns {
    private ConfiguredMobSpawns() {
    }

    public static void register() {
        add("desert_skate", tagged("has_desert_skate"), ModEntities.DESERT_SKATE.get(), ServerConfigs.DESERT_SKATE, 1);
        add("condroc_savanna", tagged("condroc_savannas"), ModEntities.VULTURE.get(), ServerConfigs.VULTURE, 1);
        add("condroc_arid", tagged("condroc_arid"), ModEntities.VULTURE.get(), ServerConfigs.VULTURE, .4);
        add("condroc_ravines", overworld().and(tagged("condroc_habitat").negate()),
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
                context.getMobSpawnSettings().addSpawn(entity,
                        SpawnDefaults.scaledWeight(config.weight().get(), factor),
                        config.minGroupSize().get(), config.maxGroupCount());
            }
        });
    }
}

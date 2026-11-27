package yagen.waitmydawn.yagens_mobs.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.ModConfigSpec;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SpawnDefaults;

public final class ServerConfigs {
    private static final Map<ResourceLocation, Spawns> BY_ENTITY = new HashMap<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final Spawns DESERT_SKATE = spawns(SpawnDefaults.DESERT_SKATE);
    public static final Spawns VULTURE = spawns(SpawnDefaults.CONDROC);
    public static final Spawns ROCK_HANGER = spawns(SpawnDefaults.ROCK_HANGER);
    public static final Spawns BUBBLE_SALAMANDER = spawns(SpawnDefaults.BUBBLE_SALAMANDER);
    public static final Spawns VELOCIPOD = spawns(SpawnDefaults.VELOCIPOD);
    public static final Spawns LUMEN_BUTTERFLY = spawns(SpawnDefaults.LUMEN_BUTTERFLY);
    public static final ModConfigSpec.IntValue ROCK_HANGER_CEILING_CLEARANCE = BUILDER
            .comment("Minimum uninterrupted clearance measured from the ceiling for natural spawns, spawn eggs, and reattachment.")
            .defineInRange("spawns.rock_hanger.minimumAirBelow", SpawnDefaults.ROCK_HANGER_CEILING_CLEARANCE, 16, 256);
    public static final ModConfigSpec.DoubleValue DESERT_SKATE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that each naturally spawned group member after the first is a juvenile.")
            .defineInRange("spawns.desert_skate.juvenileChance", SpawnDefaults.DESERT_SKATE_JUVENILE_CHANCE, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VULTURE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that a naturally spawned Condroc is a juvenile.")
            .defineInRange("spawns.condroc.juvenileChance", SpawnDefaults.CONDROC_JUVENILE_CHANCE, 0.0, 1.0);
    public static final Spawns SNOWCACHE_WEASEL = spawns(SpawnDefaults.SNOWCACHE_WEASEL);
    public static final Spawns COLLECTOR = spawns(SpawnDefaults.COLLECTOR);
    public static final Spawns KAVAT = spawns(SpawnDefaults.KAVAT);
    public static final ModConfigSpec.IntValue KAVAT_MAIN_ISLAND_SPACING = BUILDER
            .comment("Minimum distance between naturally spawning wild Kavats on the central End island. Zero disables spacing.")
            .defineInRange("spawns.kavat.mainIslandSpacing", 64, 0, 128);
    public static final NestSpawns SALVAGE_RAY = new NestSpawns(
            BUILDER.comment("Generate Salvage Ray nests and their resident groups in new End chunks.")
                    .worldRestart().define("spawns.salvage_ray.enabled", true),
            BUILDER.comment("Probability that an eligible outer-island nest location generates a structure.")
                    .worldRestart().defineInRange("spawns.salvage_ray.nestChance", .75, 0, 1),
            BUILDER.worldRestart().defineInRange("spawns.salvage_ray.minGroupSize", 2, 1, 12),
            BUILDER.worldRestart().defineInRange("spawns.salvage_ray.maxGroupSize", 3, 1, 12));
    public static final ModConfigSpec SPEC = BUILDER.build();

    public record NestSpawns(ModConfigSpec.BooleanValue enabled, ModConfigSpec.DoubleValue nestChance,
                             ModConfigSpec.IntValue minGroupSize, ModConfigSpec.IntValue maxGroupSize) {
        public boolean canSpawn() {
            return enabled.get() && nestChance.get() > 0;
        }

        public int maxGroupCount() {
            return Math.max(minGroupSize.get(), maxGroupSize.get());
        }
    }

    private ServerConfigs() {
    }

    public static Spawns forEntity(EntityType<?> type) {
        return BY_ENTITY.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private static Spawns spawns(SpawnDefaults defaults) {
        return spawns(defaults.path(), defaults.weight(), defaults.minGroupSize(), defaults.maxGroupSize());
    }
    private static Spawns spawns(String path,int weight,int min,int max) {
        BUILDER.push("spawns").push(path);
        var settings = new Spawns(
                BUILDER.comment("Enable natural spawning.").worldRestart().define("enabled", true),
                BUILDER.comment("Relative weight among the biome's eligible spawns. Zero disables this entry.")
                        .worldRestart().defineInRange("weight", weight, 0, 1000),
                BUILDER.comment("Minimum number of spawn attempts when a group is selected.")
                        .worldRestart().defineInRange("minGroupSize", min, 1, 16),
                BUILDER.comment("Maximum group size. Values below the minimum are raised to the minimum.")
                        .worldRestart().defineInRange("maxGroupSize", max, 1, 16));
        BUILDER.pop(2);
        BY_ENTITY.put(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path), settings);
        return settings;
    }

    public record Spawns(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue weight,
                         ModConfigSpec.IntValue minGroupSize, ModConfigSpec.IntValue maxGroupSize) {
        public boolean canSpawn() {
            return enabled.get() && weight.get() > 0;
        }

        public int maxGroupCount() {
            return Math.max(minGroupSize.get(), maxGroupSize.get());
        }
    }
}

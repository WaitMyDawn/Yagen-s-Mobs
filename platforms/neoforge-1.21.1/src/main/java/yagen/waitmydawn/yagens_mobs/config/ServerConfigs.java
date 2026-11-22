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
    public static final ModConfigSpec.IntValue ROCK_HANGER_CEILING_CLEARANCE = BUILDER
            .comment("Minimum uninterrupted clearance measured from the ceiling for natural spawns, spawn eggs, and reattachment.")
            .defineInRange("spawns.rock_hanger.minimumAirBelow", SpawnDefaults.ROCK_HANGER_CEILING_CLEARANCE, 16, 256);
    public static final ModConfigSpec.DoubleValue DESERT_SKATE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that each naturally spawned group member after the first is a juvenile.")
            .defineInRange("spawns.desert_skate.juvenileChance", SpawnDefaults.DESERT_SKATE_JUVENILE_CHANCE, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VULTURE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that a naturally spawned Condroc is a juvenile.")
            .defineInRange("spawns.condroc.juvenileChance", SpawnDefaults.CONDROC_JUVENILE_CHANCE, 0.0, 1.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfigs() {
    }

    public static Spawns forEntity(EntityType<?> type) {
        return BY_ENTITY.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private static Spawns spawns(SpawnDefaults defaults) {
        BUILDER.push("spawns").push(defaults.path());
        var settings = new Spawns(
                BUILDER.comment("Enable natural spawning.").worldRestart().define("enabled", true),
                BUILDER.comment("Relative weight among the biome's eligible spawns. Zero disables this entry.")
                        .worldRestart().defineInRange("weight", defaults.weight(), 0, 1000),
                BUILDER.comment("Minimum number of spawn attempts when a group is selected.")
                        .worldRestart().defineInRange("minGroupSize", defaults.minGroupSize(), 1, 16),
                BUILDER.comment("Maximum group size. Values below the minimum are raised to the minimum.")
                        .worldRestart().defineInRange("maxGroupSize", defaults.maxGroupSize(), 1, 16));
        BUILDER.pop(2);
        BY_ENTITY.put(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, defaults.path()), settings);
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

package yagen.waitmydawn.yagens_mobs.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.ModConfigSpec;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ServerConfigs {
    private static final Map<ResourceLocation, Spawns> BY_ENTITY = new HashMap<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final Spawns DESERT_SKATE = spawns("desert_skate", 2, 3, 4);
    public static final Spawns VULTURE = spawns("condroc", 8, 1, 3);
    public static final Spawns ROCK_HANGER = spawns("rock_hanger", 10, 1, 1);
    public static final Spawns BUBBLE_SALAMANDER = spawns("bubble_salamander", 5, 1, 2);
    public static final Spawns VELOCIPOD = spawns("velocipod", 6, 1, 2);
    public static final ModConfigSpec.IntValue ROCK_HANGER_CEILING_CLEARANCE = BUILDER
            .comment("Minimum uninterrupted clearance measured from the ceiling for natural spawns, spawn eggs, and reattachment.")
            .defineInRange("spawns.rock_hanger.minimumAirBelow", 16, 16, 256);
    public static final ModConfigSpec.DoubleValue DESERT_SKATE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that each naturally spawned group member after the first is a juvenile.")
            .defineInRange("spawns.desert_skate.juvenileChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VULTURE_JUVENILE_CHANCE = BUILDER
            .comment("Chance that a naturally spawned Condroc is a juvenile.")
            .defineInRange("spawns.condroc.juvenileChance", 0.20, 0.0, 1.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfigs() {
    }

    public static Spawns forEntity(EntityType<?> type) {
        return BY_ENTITY.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private static Spawns spawns(String path, int weight, int min, int max) {
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

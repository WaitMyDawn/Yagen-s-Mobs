package yagen.waitmydawn.yagens_mobs.spawn;

import com.mojang.serialization.Codec;
import java.util.Optional;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SpawnDefaults;

public record ConfiguredMobSpawns(HolderSet<Biome> biomes, EntityType<?> entityType, double weightFactor,
                                  Optional<HolderSet<Biome>> excludedBiomes) implements BiomeModifier {
    private static final MapCodec<ConfiguredMobSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfiguredMobSpawns::biomes),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ConfiguredMobSpawns::entityType),
            Codec.doubleRange(0.01, 1.0).optionalFieldOf("weight_factor", 1.0).forGetter(ConfiguredMobSpawns::weightFactor),
            Biome.LIST_CODEC.optionalFieldOf("excluded_biomes").forGetter(ConfiguredMobSpawns::excludedBiomes)
    ).apply(instance, ConfiguredMobSpawns::new));

    public static void register(IEventBus modBus) {
        var serializers = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ModIdentity.ID);
        serializers.register("configured_mob_spawns", () -> CODEC);
        serializers.register(modBus);
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome) || excludedBiomes.filter(set -> set.contains(biome)).isPresent()) {
            return;
        }
        // NeoForge loads the world's server config before applying biome modifiers.
        var spawns = ServerConfigs.forEntity(entityType);
        if (spawns != null && spawns.canSpawn()) {
            builder.getMobSpawnSettings().addSpawn(entityType.getCategory(), new MobSpawnSettings.SpawnerData(
                    entityType, SpawnDefaults.scaledWeight(spawns.weight().get(), weightFactor),
                    spawns.minGroupSize().get(), spawns.maxGroupCount()));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}

package yagen.waitmydawn.yagens_mobs.registries;

import yagen.waitmydawn.yagens_mobs.entity.collector.Collector;
import yagen.waitmydawn.yagens_mobs.entity.vulture.LongFeatherArrow;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;
import java.util.function.Supplier;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.SpawnPlacements;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.DesertSkate;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.SkateSpike;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkGrip;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkLine;

public final class ModEntities {
    public static final Supplier<EntityType<BubbleSalamander>> BUBBLE_SALAMANDER = entity(
            "bubble_salamander", id -> EntityType.Builder.<BubbleSalamander>of(yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.ShearableBubbleSalamander::new, MobCategory.WATER_CREATURE)
                    .sized(.95F, 1.10F).eyeHeight(.94F).clientTrackingRange(8).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<DesertSkate>> DESERT_SKATE = entity("desert_skate",
            id -> EntityType.Builder.of(DesertSkate::new, MobCategory.MONSTER)
                    .sized(0.94F, 0.55F).eyeHeight(0.34F).clientTrackingRange(8).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<SkateSpike>> SKATE_SPIKE = entity("skate_spike",
            id -> EntityType.Builder.<SkateSpike>of(SkateSpike::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(1).noSave().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<LongFeatherArrow>> LONG_FEATHER_ARROW = entity("long_feather_arrow",
            id -> EntityType.Builder.<LongFeatherArrow>of(LongFeatherArrow::new, MobCategory.MISC)
                    .sized(.5F, .5F).clientTrackingRange(8).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<Vulture>> VULTURE = entity("condroc",
            id -> EntityType.Builder.of(Vulture::new, MobCategory.CREATURE)
                    .sized(.72F, 1.15F).eyeHeight(1.02F).clientTrackingRange(10).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Supplier<EntityType<RockHanger>> ROCK_HANGER = entity("rock_hanger",
            id -> EntityType.Builder.of(RockHanger::new, MobCategory.MONSTER)
                    .sized(1.38F, 0.76F).eyeHeight(0.36F).clientTrackingRange(12).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Supplier<EntityType<RockSilkLine>> ROCK_SILK_LINE = entity(
            "rock_silk_line", id -> EntityType.Builder.<RockSilkLine>of(RockSilkLine::new, MobCategory.MISC)
                    .sized(0.28F, 0.28F).clientTrackingRange(32).updateInterval(20).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Supplier<EntityType<RockSilkGrip>> ROCK_SILK_GRIP = entity(
            "rock_silk_grip", id -> EntityType.Builder.<RockSilkGrip>of(RockSilkGrip::new, MobCategory.MISC)
                    .sized(0.05F, 0.05F).clientTrackingRange(8).updateInterval(1).noSave().noSummon()
                    .fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Supplier<EntityType<Velocipod>> VELOCIPOD = entity("velocipod",
            id -> EntityType.Builder.of(Velocipod::new, MobCategory.CREATURE)
                    .sized(1.24F, 1.73F).eyeHeight(1.40F).clientTrackingRange(10).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Supplier<EntityType<Collector>> COLLECTOR = entity("collector", id ->
            EntityType.Builder.of(Collector::new, MobCategory.CREATURE).sized(1.05F,1.45F).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<Kavat>> KAVAT = entity("kavat", id ->
            EntityType.Builder.of(Kavat::new, MobCategory.CREATURE).sized(.62F,.95F).eyeHeight(.69F).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final Supplier<EntityType<Kavat>> KAVAT_DECOY = entity("kavat_decoy", id ->
            EntityType.Builder.of(Kavat::new, MobCategory.MISC).sized(.62F,.95F).noSave().noSummon().clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntities() {
    }

    private static <T extends Entity> Supplier<EntityType<T>> entity(
            String path, Function<Identifier, EntityType<T>> factory) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
        EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, id, factory.apply(id));
        return () -> type;
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(COLLECTOR.get(), Collector.attributes().build());
        FabricDefaultAttributeRegistry.register(KAVAT.get(), Kavat.attributes());
        FabricDefaultAttributeRegistry.register(KAVAT_DECOY.get(), Kavat.attributes());
        SpawnPlacements.register(COLLECTOR.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Collector::placement);
        SpawnPlacements.register(KAVAT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, KavatSpawns::placement);
        FabricDefaultAttributeRegistry.register(BUBBLE_SALAMANDER.get(), BubbleSalamander.createAttributes());
        FabricDefaultAttributeRegistry.register(DESERT_SKATE.get(), DesertSkate.createAttributes());
        FabricDefaultAttributeRegistry.register(VULTURE.get(), Vulture.createAttributes());
        FabricDefaultAttributeRegistry.register(ROCK_HANGER.get(), RockHanger.createAttributes());
        FabricDefaultAttributeRegistry.register(VELOCIPOD.get(), Velocipod.createAttributes());
        SpawnPlacements.register(VELOCIPOD.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Velocipod::checkSpawnPlacement);
        SpawnPlacements.register(BUBBLE_SALAMANDER.get(), SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BubbleSalamander::checkSpawnPlacement);
        SpawnPlacements.register(DESERT_SKATE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DesertSkate::checkSpawnPlacement);
        SpawnPlacements.register(VULTURE.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING, Vulture::checkSpawnPlacement);
        SpawnPlacements.register(ROCK_HANGER.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, RockHanger::checkSpawnPlacement);
    }
}

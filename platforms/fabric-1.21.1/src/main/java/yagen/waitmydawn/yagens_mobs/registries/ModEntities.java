package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacements;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.DesertSkate;
import yagen.waitmydawn.yagens_mobs.entity.RockHanger;
import yagen.waitmydawn.yagens_mobs.entity.SkateSpike;
import yagen.waitmydawn.yagens_mobs.entity.Vulture;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkGrip;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkLine;

public final class ModEntities {
    public static final Supplier<EntityType<BubbleSalamander>> BUBBLE_SALAMANDER = entity(
            "bubble_salamander", id -> EntityType.Builder.of(BubbleSalamander::new, MobCategory.WATER_CREATURE)
                    .sized(.95F, 1.10F).eyeHeight(.94F).clientTrackingRange(8).updateInterval(2).build(id.toString()));
    public static final Supplier<EntityType<DesertSkate>> DESERT_SKATE = entity("desert_skate",
            id -> EntityType.Builder.of(DesertSkate::new, MobCategory.MONSTER)
                    .sized(0.94F, 0.55F).eyeHeight(0.34F).clientTrackingRange(8).updateInterval(2).build(id.toString()));
    public static final Supplier<EntityType<SkateSpike>> SKATE_SPIKE = entity("skate_spike",
            id -> EntityType.Builder.<SkateSpike>of(SkateSpike::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(1).noSave().build(id.toString()));
    public static final Supplier<EntityType<Vulture>> VULTURE = entity("condroc",
            id -> EntityType.Builder.of(Vulture::new, MobCategory.CREATURE)
                    .sized(.72F, 1.15F).eyeHeight(1.02F).clientTrackingRange(10).updateInterval(2).build(id.toString()));

    public static final Supplier<EntityType<RockHanger>> ROCK_HANGER = entity("rock_hanger",
            id -> EntityType.Builder.of(RockHanger::new, MobCategory.MONSTER)
                    .sized(1.38F, 0.76F).eyeHeight(0.36F).clientTrackingRange(12).updateInterval(1).build(id.toString()));

    public static final Supplier<EntityType<RockSilkLine>> ROCK_SILK_LINE = entity(
            "rock_silk_line", id -> EntityType.Builder.<RockSilkLine>of(RockSilkLine::new, MobCategory.MISC)
                    .sized(0.28F, 0.28F).clientTrackingRange(16).updateInterval(20).fireImmune().build(id.toString()));

    public static final Supplier<EntityType<RockSilkGrip>> ROCK_SILK_GRIP = entity(
            "rock_silk_grip", id -> EntityType.Builder.<RockSilkGrip>of(RockSilkGrip::new, MobCategory.MISC)
                    .sized(0.05F, 0.05F).clientTrackingRange(8).updateInterval(1).noSave().noSummon()
                    .fireImmune().build(id.toString()));

    public static final Supplier<EntityType<Velocipod>> VELOCIPOD = entity("velocipod",
            id -> EntityType.Builder.of(Velocipod::new, MobCategory.CREATURE)
                    .sized(1.24F, 1.73F).eyeHeight(1.40F).clientTrackingRange(10).updateInterval(1).build(id.toString()));

    private ModEntities() {
    }

    private static <T extends Entity> Supplier<EntityType<T>> entity(
            String path, Function<ResourceLocation, EntityType<T>> factory) {
        var id = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
        EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, id, factory.apply(id));
        return () -> type;
    }

    public static void register() {
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
        SpawnPlacements.register(VULTURE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Vulture::checkSpawnPlacement);
        SpawnPlacements.register(ROCK_HANGER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, RockHanger::checkSpawnPlacement);
    }
}

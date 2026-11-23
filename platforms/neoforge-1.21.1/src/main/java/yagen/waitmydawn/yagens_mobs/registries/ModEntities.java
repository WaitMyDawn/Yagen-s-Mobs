package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
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
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BubbleSalamander>> BUBBLE_SALAMANDER = TYPES.register(
            "bubble_salamander", id -> EntityType.Builder.of(BubbleSalamander::new, MobCategory.WATER_CREATURE)
                    .sized(.95F, 1.10F).eyeHeight(.94F).clientTrackingRange(8).updateInterval(2).build(id.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<DesertSkate>> DESERT_SKATE = TYPES.register("desert_skate",
            id -> EntityType.Builder.of(DesertSkate::new, MobCategory.MONSTER)
                    .sized(0.94F, 0.55F).eyeHeight(0.34F).clientTrackingRange(8).updateInterval(2).build(id.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<SkateSpike>> SKATE_SPIKE = TYPES.register("skate_spike",
            id -> EntityType.Builder.<SkateSpike>of(SkateSpike::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(1).noSave().build(id.toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<Vulture>> VULTURE = TYPES.register("condroc",
            id -> EntityType.Builder.of(Vulture::new, MobCategory.CREATURE)
                    .sized(.72F, 1.15F).eyeHeight(1.02F).clientTrackingRange(10).updateInterval(2).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RockHanger>> ROCK_HANGER = TYPES.register("rock_hanger",
            id -> EntityType.Builder.of(RockHanger::new, MobCategory.MONSTER)
                    .sized(1.38F, 0.76F).eyeHeight(0.36F).clientTrackingRange(12).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RockSilkLine>> ROCK_SILK_LINE = TYPES.register(
            "rock_silk_line", id -> EntityType.Builder.<RockSilkLine>of(RockSilkLine::new, MobCategory.MISC)
                    .sized(0.28F, 0.28F).clientTrackingRange(32).updateInterval(20).fireImmune().build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RockSilkGrip>> ROCK_SILK_GRIP = TYPES.register(
            "rock_silk_grip", id -> EntityType.Builder.<RockSilkGrip>of(RockSilkGrip::new, MobCategory.MISC)
                    .sized(0.05F, 0.05F).clientTrackingRange(8).updateInterval(1).noSave().noSummon()
                    .fireImmune().build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<Velocipod>> VELOCIPOD = TYPES.register("velocipod",
            id -> EntityType.Builder.of(Velocipod::new, MobCategory.CREATURE)
                    .sized(1.24F, 1.73F).eyeHeight(1.40F).clientTrackingRange(10).updateInterval(1).build(id.toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener(ModEntities::attributes);
        modBus.addListener(ModEntities::placements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(BUBBLE_SALAMANDER.get(), BubbleSalamander.createAttributes().build());
        event.put(DESERT_SKATE.get(), DesertSkate.createAttributes().build());
        event.put(VULTURE.get(), Vulture.createAttributes().build());
        event.put(ROCK_HANGER.get(), RockHanger.createAttributes().build());
        event.put(VELOCIPOD.get(), Velocipod.createAttributes().build());
    }

    private static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(VELOCIPOD.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Velocipod::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(BUBBLE_SALAMANDER.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BubbleSalamander::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(DESERT_SKATE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DesertSkate::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(VULTURE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Vulture::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ROCK_HANGER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RockHanger::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}

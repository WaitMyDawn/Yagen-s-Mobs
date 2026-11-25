package yagen.waitmydawn.yagens_mobs.registries;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.DesertSkate;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.SkateSpike;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;
import yagen.waitmydawn.yagens_mobs.entity.vulture.Vulture;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkGrip;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkLine;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);

    public static final RegistryObject<EntityType<BubbleSalamander>> BUBBLE_SALAMANDER = TYPES.register(
            "bubble_salamander", () -> EntityType.Builder.of(BubbleSalamander::new, MobCategory.WATER_CREATURE)
                    .sized(.95F, 1.10F).clientTrackingRange(8).updateInterval(2).build(ModIdentity.ID + ":bubble_salamander"));
    public static final RegistryObject<EntityType<DesertSkate>> DESERT_SKATE = TYPES.register("desert_skate",
            () -> EntityType.Builder.of(DesertSkate::new, MobCategory.MONSTER)
                    .sized(0.94F, 0.55F).clientTrackingRange(8).updateInterval(2).build(ModIdentity.ID + ":desert_skate"));
    public static final RegistryObject<EntityType<SkateSpike>> SKATE_SPIKE = TYPES.register("skate_spike",
            () -> EntityType.Builder.<SkateSpike>of(SkateSpike::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(1).noSave().build(ModIdentity.ID + ":skate_spike"));
    public static final RegistryObject<EntityType<Vulture>> VULTURE = TYPES.register("condroc",
            () -> EntityType.Builder.of(Vulture::new, MobCategory.CREATURE)
                    .sized(.72F, 1.15F).clientTrackingRange(10).updateInterval(2).build(ModIdentity.ID + ":condroc"));

    public static final RegistryObject<EntityType<RockHanger>> ROCK_HANGER = TYPES.register("rock_hanger",
            () -> EntityType.Builder.of(RockHanger::new, MobCategory.MONSTER)
                    .sized(1.38F, 0.76F).clientTrackingRange(12).updateInterval(1).build(ModIdentity.ID + ":rock_hanger"));

    public static final RegistryObject<EntityType<RockSilkLine>> ROCK_SILK_LINE = TYPES.register(
            "rock_silk_line", () -> EntityType.Builder.<RockSilkLine>of(RockSilkLine::new, MobCategory.MISC)
                    .sized(0.28F, 0.28F).clientTrackingRange(32).updateInterval(20).fireImmune().build(ModIdentity.ID + ":rock_silk_line"));

    public static final RegistryObject<EntityType<RockSilkGrip>> ROCK_SILK_GRIP = TYPES.register(
            "rock_silk_grip", () -> EntityType.Builder.<RockSilkGrip>of(RockSilkGrip::new, MobCategory.MISC)
                    .sized(0.05F, 0.05F).clientTrackingRange(8).updateInterval(1).noSave().noSummon()
                    .fireImmune().build(ModIdentity.ID + ":rock_silk_grip"));

    public static final RegistryObject<EntityType<Velocipod>> VELOCIPOD = TYPES.register("velocipod",
            () -> EntityType.Builder.of(Velocipod::new, MobCategory.CREATURE)
                    .sized(1.24F, 1.73F).clientTrackingRange(10).updateInterval(1).build(ModIdentity.ID + ":velocipod"));

    public static final RegistryObject<EntityType<Kavat>> KAVAT = TYPES.register("kavat",
            () -> EntityType.Builder.of(Kavat::new, MobCategory.CREATURE).sized(.62F,.95F).clientTrackingRange(10).build(ModIdentity.ID + ":kavat"));
    public static final RegistryObject<EntityType<Kavat>> KAVAT_DECOY = TYPES.register("kavat_decoy",
            () -> EntityType.Builder.of(Kavat::new, MobCategory.MISC).sized(.62F,.95F).noSave().noSummon().clientTrackingRange(10).build(ModIdentity.ID + ":kavat_decoy"));
    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener(ModEntities::attributes);
        modBus.addListener(ModEntities::placements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(KAVAT.get(), Kavat.attributes().build());
        event.put(KAVAT_DECOY.get(), Kavat.attributes().build());
        event.put(BUBBLE_SALAMANDER.get(), BubbleSalamander.createAttributes().build());
        event.put(DESERT_SKATE.get(), DesertSkate.createAttributes().build());
        event.put(VULTURE.get(), Vulture.createAttributes().build());
        event.put(ROCK_HANGER.get(), RockHanger.createAttributes().build());
        event.put(VELOCIPOD.get(), Velocipod.createAttributes().build());
    }

    private static void placements(SpawnPlacementRegisterEvent event) {
        event.register(KAVAT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, KavatSpawns::placement, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(VELOCIPOD.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Velocipod::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(BUBBLE_SALAMANDER.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BubbleSalamander::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(DESERT_SKATE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DesertSkate::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(VULTURE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Vulture::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ROCK_HANGER.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RockHanger::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}

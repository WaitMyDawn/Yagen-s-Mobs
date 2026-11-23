package yagen.waitmydawn.yagens_mobs.salvage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class SalvageRays {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ModIdentity.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ModIdentity.ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ModIdentity.ID);

    public static final RegistryObject<EntityType<SalvageRay>> RAY = ENTITIES.register("salvage_ray", () -> EntityType.Builder.of(SalvageRay::new, MobCategory.CREATURE)
            .sized(1.36F, .51F).clientTrackingRange(12).updateInterval(2).build(id("salvage_ray").toString()));
    public static final RegistryObject<SalvageNestBox> NEST_BOX = BLOCKS.register("salvage_nest_box", () ->
            new SalvageNestBox(BlockBehaviour.Properties.of().strength(2, 6).sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<BlockEntityType<SalvageNestBoxEntity>> NEST_BOX_ENTITY = BLOCK_ENTITIES.register("salvage_nest_box", () ->
            BlockEntityType.Builder.of(SalvageNestBoxEntity::new, NEST_BOX.get()).build(null));
    static final RegistryObject<StructureType<SalvageNestStructure>> NEST = STRUCTURES.register("salvage_ray_nest", () -> (StructureType<SalvageNestStructure>) () -> SalvageNestStructure.CODEC);
    static final RegistryObject<StructurePieceType.ContextlessType> NEST_PIECE = PIECES.register("salvage_ray_nest", () -> (StructurePieceType.ContextlessType) SalvageNestPiece::new);

    private SalvageRays() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ModIdentity.ID, path);
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        STRUCTURES.register(bus);
        PIECES.register(bus);
        bus.addListener(SalvageRays::attributes);
        SalvageNestMenu.register(bus);
        MinecraftForge.EVENT_BUS.addListener(SalvageRoutes::onCompass);
        MinecraftForge.EVENT_BUS.addListener(SalvagePlayerMotion::tick);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RAY.get(), SalvageRay.attributes().build());
    }
}

package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SalvageRays {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModIdentity.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModIdentity.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ModIdentity.ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ModIdentity.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SalvageRay>> RAY = ENTITIES.register("salvage_ray", () -> EntityType.Builder.of(SalvageRay::new, MobCategory.CREATURE)
            .sized(1.36F, .51F).eyeHeight(.30F).clientTrackingRange(12).updateInterval(2).build(id("salvage_ray").toString()));
    public static final DeferredBlock<SalvageNestBox> NEST_BOX = BLOCKS.register("salvage_nest_box", () ->
            new SalvageNestBox(BlockBehaviour.Properties.of().strength(2, 6).sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvageNestBoxEntity>> NEST_BOX_ENTITY = BLOCK_ENTITIES.register("salvage_nest_box", () ->
            BlockEntityType.Builder.of(SalvageNestBoxEntity::new, NEST_BOX.get()).build(null));
    public static final DeferredItem<BlockItem> NEST_BOX_ITEM = ITEMS.register("salvage_nest_box", () -> new BlockItem(NEST_BOX.get(), new Item.Properties()));
    private static final DeferredItem<DeferredSpawnEggItem> EGG = ITEMS.register("salvage_ray_spawn_egg",
            () -> new DeferredSpawnEggItem(RAY, 0x2b2138, 0x987BA7, new Item.Properties()));
    static final DeferredHolder<StructureType<?>, StructureType<SalvageNestStructure>> NEST = STRUCTURES.register("salvage_ray_nest", () -> (StructureType<SalvageNestStructure>) () -> SalvageNestStructure.CODEC);
    static final DeferredHolder<StructurePieceType, StructurePieceType.ContextlessType> NEST_PIECE = PIECES.register("salvage_ray_nest", () -> (StructurePieceType.ContextlessType) SalvageNestPiece::new);

    private SalvageRays() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        STRUCTURES.register(bus);
        PIECES.register(bus);
        bus.addListener(SalvageRays::attributes);
        bus.addListener(SalvageRays::creativeContents);
        bus.addListener(SalvageRoutes::register);
        SalvageNestMenu.register(bus);
        NeoForge.EVENT_BUS.addListener(SalvageRoutes::onCompass);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RAY.get(), SalvageRay.attributes().build());
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.YAGENS_MOBS.getKey())) {
            event.insertBefore(new ItemStack(ModItems.DESERT_SKATE_SPAWN_EGG.get()),
                    NEST_BOX_ITEM.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(EGG.get());
        }
    }
}

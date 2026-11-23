package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.PushReaction;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SalvageRays {
    private static final EntityType<SalvageRay> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("salvage_ray"),
            EntityType.Builder.of(SalvageRay::new, MobCategory.CREATURE).sized(1.36F, .51F).eyeHeight(.30F)
                    .clientTrackingRange(12).updateInterval(2).build(id("salvage_ray").toString()));
    public static final Supplier<EntityType<SalvageRay>> RAY = () -> TYPE;
    private static final SalvageNestBox BOX = Registry.register(BuiltInRegistries.BLOCK, id("salvage_nest_box"),
            new SalvageNestBox(BlockBehaviour.Properties.of().strength(2, 6).sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)));
    public static final Supplier<SalvageNestBox> NEST_BOX = () -> BOX;
    private static final BlockEntityType<SalvageNestBoxEntity> BOX_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("salvage_nest_box"),
            net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(SalvageNestBoxEntity::new, BOX).build());
    public static final Supplier<BlockEntityType<SalvageNestBoxEntity>> NEST_BOX_ENTITY = () -> BOX_ENTITY;
    private static final StructureType<SalvageNestStructure> STRUCTURE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            id("salvage_ray_nest"), () -> SalvageNestStructure.CODEC);
    static final Supplier<StructureType<SalvageNestStructure>> NEST = () -> STRUCTURE;
    private static final StructurePieceType.ContextlessType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
            id("salvage_ray_nest"), (StructurePieceType.ContextlessType) SalvageNestPiece::new);
    static final Supplier<StructurePieceType.ContextlessType> NEST_PIECE = () -> PIECE;

    private SalvageRays() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    public static void register() {
        ModItems.item("salvage_nest_box", () -> new BlockItem(BOX, new Item.Properties()));
        ModItems.item("salvage_ray_spawn_egg", () -> new SpawnEggItem(TYPE, 0x2b2138, 0x987BA7, new Item.Properties()));
        FabricDefaultAttributeRegistry.register(TYPE, SalvageRay.attributes());
        SalvageRoutes.register();
        SalvageNestMenu.register();
        UseBlockCallback.EVENT.register(SalvageRoutes::onCompass);
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SalvageNestBoxEntity box) {
                box.onLoad();
            }
        });
    }
}

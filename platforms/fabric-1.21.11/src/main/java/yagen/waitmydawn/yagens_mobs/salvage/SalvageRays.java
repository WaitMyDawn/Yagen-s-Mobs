package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
                    .clientTrackingRange(12).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, id("salvage_ray"))));
    public static final Supplier<EntityType<SalvageRay>> RAY = () -> TYPE;
    private static final SalvageNestBox BOX = Registry.register(BuiltInRegistries.BLOCK, id("salvage_nest_box"),
            new SalvageNestBox(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("salvage_nest_box"))).strength(2, 6).sound(SoundType.STONE)
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

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    public static void register() {
        ModItems.blockItem("salvage_nest_box", BOX);
        ModItems.item("salvage_ray_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(TYPE)));
        FabricDefaultAttributeRegistry.register(TYPE, SalvageRay.attributes());
        SalvageRoutes.register();
        SalvageNestMenu.TYPE.get();
        UseBlockCallback.EVENT.register(SalvageRoutes::onCompass);
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SalvageNestBoxEntity box) {
                box.onLoad();
            }
        });
    }
}

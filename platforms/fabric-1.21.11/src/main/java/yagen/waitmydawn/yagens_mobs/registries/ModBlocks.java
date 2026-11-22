package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.transport.MailParcelBlock;
import yagen.waitmydawn.yagens_mobs.transport.MailParcelEntity;
import yagen.waitmydawn.yagens_mobs.transport.VultureNestBlock;
import yagen.waitmydawn.yagens_mobs.transport.VultureNestEntity;
import yagen.waitmydawn.yagens_mobs.transport.VulturePostBlock;
import yagen.waitmydawn.yagens_mobs.transport.VulturePostEntity;

public final class ModBlocks {
    public static final Supplier<VulturePostBlock> VULTURE_POST = block("vulture_post",
            properties -> new VulturePostBlock(properties.strength(2.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final Supplier<VultureNestBlock> VULTURE_NEST = block("vulture_nest",
            properties -> new VultureNestBlock(properties.strength(.3F).sound(SoundType.GRASS)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final Supplier<MailParcelBlock> MAIL_PARCEL = block("mail_parcel",
            properties -> new MailParcelBlock(properties.strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.WOOL).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final Supplier<BlockEntityType<VulturePostEntity>> VULTURE_POST_ENTITY =
            blockEntity("vulture_post", () -> FabricBlockEntityTypeBuilder.create(
                    VulturePostEntity::new, VULTURE_POST.get()).build());
    public static final Supplier<BlockEntityType<VultureNestEntity>> VULTURE_NEST_ENTITY =
            blockEntity("vulture_nest", () -> FabricBlockEntityTypeBuilder.create(
                    VultureNestEntity::new, VULTURE_NEST.get()).build());
    public static final Supplier<BlockEntityType<MailParcelEntity>> MAIL_PARCEL_ENTITY =
            blockEntity("mail_parcel", () -> FabricBlockEntityTypeBuilder.create(
                    MailParcelEntity::new, MAIL_PARCEL.get()).build());

    private ModBlocks() {}

    private static <T extends Block> Supplier<T> block(String name, Function<BlockBehaviour.Properties, T> factory) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, name);
        T block = Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))));
        return () -> block;
    }

    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> blockEntity(
            String name, Supplier<BlockEntityType<T>> factory) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, name);
        BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, factory.get());
        return () -> type;
    }

    public static void register() {
    }
}

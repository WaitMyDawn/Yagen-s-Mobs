package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.transport.MailParcelBlock;
import yagen.waitmydawn.yagens_mobs.transport.MailParcelEntity;
import yagen.waitmydawn.yagens_mobs.transport.VultureNestBlock;
import yagen.waitmydawn.yagens_mobs.transport.VultureNestEntity;
import yagen.waitmydawn.yagens_mobs.transport.VulturePostBlock;
import yagen.waitmydawn.yagens_mobs.transport.VulturePostEntity;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModIdentity.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModIdentity.ID);

    public static final DeferredHolder<Block, VulturePostBlock> VULTURE_POST = BLOCKS.register("vulture_post",
            () -> new VulturePostBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Block, VultureNestBlock> VULTURE_NEST = BLOCKS.register("vulture_nest",
            () -> new VultureNestBlock(BlockBehaviour.Properties.of().strength(.3F).sound(SoundType.GRASS)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredHolder<Block, MailParcelBlock> MAIL_PARCEL = BLOCKS.register("mail_parcel",
            () -> new MailParcelBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.WOOL).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VulturePostEntity>> VULTURE_POST_ENTITY =
            ENTITIES.register("vulture_post", () -> BlockEntityType.Builder.of(
                    VulturePostEntity::new, VULTURE_POST.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VultureNestEntity>> VULTURE_NEST_ENTITY =
            ENTITIES.register("vulture_nest", () -> BlockEntityType.Builder.of(
                    VultureNestEntity::new, VULTURE_NEST.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailParcelEntity>> MAIL_PARCEL_ENTITY =
            ENTITIES.register("mail_parcel", () -> BlockEntityType.Builder.of(
                    MailParcelEntity::new, MAIL_PARCEL.get()).build(null));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ENTITIES.register(bus);
    }
}

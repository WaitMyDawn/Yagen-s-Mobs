package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import java.util.Map;
import java.util.EnumMap;
import net.minecraft.core.registries.Registries;
import yagen.waitmydawn.yagens_mobs.entity.kavat.Incubator;
import yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderVariants;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderBlock;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailParcelBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailParcelEntity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestEntity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostEntity;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModIdentity.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModIdentity.ID);

    public static final DeferredHolder<Block, yagen.waitmydawn.yagens_mobs.entity.salvage.VoidCloudBlock> VOID_CLOUD =
            BLOCKS.register("void_cloud", () -> new yagen.waitmydawn.yagens_mobs.entity.salvage.VoidCloudBlock(
                    BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).sound(SoundType.WOOL)
                            .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

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

    public static final DeferredHolder<Block, IncubatorBlock> INCUBATOR = BLOCKS.register("kavat_incubator",
            () -> new IncubatorBlock.Core(BlockBehaviour.Properties.of().dynamicShape().strength(3).sound(SoundType.METAL)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Block, IncubatorBlock.Part> INCUBATOR_PART = BLOCKS.register("kavat_incubator_part",
            () -> new IncubatorBlock.Part(BlockBehaviour.Properties.ofFullCopy(INCUBATOR.get()).dropsLike(INCUBATOR.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Incubator>> INCUBATOR_ENTITY =
            ENTITIES.register("kavat_incubator", () -> BlockEntityType.Builder.of(Incubator::new, INCUBATOR.get()).build(null));
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModIdentity.ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorMenu>> INCUBATOR_MENU =
            MENUS.register("kavat_incubator", () -> new net.minecraft.world.inventory.MenuType<>(yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<Block, AirBladderBlock> AIR_BLADDER = BLOCKS.register("air_bladder",
            () -> new AirBladderBlock.Legacy(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noCollission().strength(.25F).sound(SoundType.SLIME_BLOCK)));
    public static final Map<BubbleSalamanderColor, Supplier<AirBladderBlock>> AIR_BLADDERS = registerBladders();

    private static Map<BubbleSalamanderColor, Supplier<AirBladderBlock>> registerBladders() {
        var variants = new EnumMap<BubbleSalamanderColor, Supplier<AirBladderBlock>>(BubbleSalamanderColor.class);
        for (var color : BubbleSalamanderColor.values()) {
            variants.put(color, BLOCKS.register(AirBladderVariants.path(color),
                    () -> new AirBladderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noCollission().strength(.25F).sound(SoundType.SLIME_BLOCK), color)));
        }
        return java.util.Collections.unmodifiableMap(variants);
    }

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BladderFluid.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
    }
}

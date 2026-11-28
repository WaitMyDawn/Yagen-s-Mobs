package yagen.waitmydawn.yagens_mobs.registries;

import java.util.Map;
import java.util.EnumMap;
import yagen.waitmydawn.yagens_mobs.entity.salvage.VoidCloudBlock;
import yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorMenu;
import yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorBlock;
import yagen.waitmydawn.yagens_mobs.entity.kavat.Incubator;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderVariants;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailParcelBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailParcelEntity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VultureNestEntity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostEntity;

public final class ModBlocks {
    public static final Supplier<VoidCloudBlock> VOID_CLOUD = block("void_cloud",
            () -> new VoidCloudBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).sound(SoundType.WOOL)
                    .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final Supplier<VulturePostBlock> VULTURE_POST = block("vulture_post",
            () -> new VulturePostBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final Supplier<VultureNestBlock> VULTURE_NEST = block("vulture_nest",
            () -> new VultureNestBlock(BlockBehaviour.Properties.of().strength(.3F).sound(SoundType.GRASS)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final Supplier<MailParcelBlock> MAIL_PARCEL = block("mail_parcel",
            () -> new MailParcelBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
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

    public static final Supplier<IncubatorBlock> INCUBATOR = block("kavat_incubator",
            () -> new IncubatorBlock.Core(BlockBehaviour.Properties.of().dynamicShape().strength(3).sound(SoundType.METAL)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final Supplier<IncubatorBlock.Part> INCUBATOR_PART = block("kavat_incubator_part",
            () -> new IncubatorBlock.Part(BlockBehaviour.Properties.ofFullCopy(INCUBATOR.get()).dropsLike(INCUBATOR.get())));
    public static final Supplier<BlockEntityType<Incubator>> INCUBATOR_ENTITY = blockEntity("kavat_incubator",
            () -> FabricBlockEntityTypeBuilder.create(Incubator::new, INCUBATOR.get()).build());
    private static final net.minecraft.world.inventory.MenuType<IncubatorMenu> MENU = Registry.register(BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "kavat_incubator"),
            new net.minecraft.world.inventory.MenuType<>(IncubatorMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
    public static final Supplier<net.minecraft.world.inventory.MenuType<IncubatorMenu>> INCUBATOR_MENU = () -> MENU;

    public static final Supplier<AirBladderBlock> AIR_BLADDER = block("air_bladder",
            () -> new AirBladderBlock.Legacy(BlockBehaviour.Properties.of().noOcclusion().noCollission().strength(.25F).sound(SoundType.SLIME_BLOCK)));
    public static final Map<BubbleSalamanderColor, Supplier<AirBladderBlock>> AIR_BLADDERS = registerBladders();

    private static Map<BubbleSalamanderColor, Supplier<AirBladderBlock>> registerBladders() {
        var variants = new EnumMap<BubbleSalamanderColor, Supplier<AirBladderBlock>>(BubbleSalamanderColor.class);
        for (var color : BubbleSalamanderColor.values()) {
            variants.put(color, block(AirBladderVariants.path(color),
                    () -> new AirBladderBlock(BlockBehaviour.Properties.of().noOcclusion().noCollission().strength(.25F).sound(SoundType.SLIME_BLOCK), color)));
        }
        return java.util.Collections.unmodifiableMap(variants);
    }

    private ModBlocks() {}

    private static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        var id = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
        T block = Registry.register(BuiltInRegistries.BLOCK, id, factory.get());
        return () -> block;
    }

    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> blockEntity(
            String name, Supplier<BlockEntityType<T>> factory) {
        var id = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
        BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, factory.get());
        return () -> type;
    }

    public static void register() {
    }
}

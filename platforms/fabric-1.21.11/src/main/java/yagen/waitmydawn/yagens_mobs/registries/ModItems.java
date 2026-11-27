package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.world.level.block.DispenserBlock;
import yagen.waitmydawn.yagens_mobs.entity.vulture.LongFeatherArrowItem;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheFur;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderBlock;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;
import yagen.waitmydawn.yagens_mobs.guide.FieldGuide;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;
import java.util.function.Supplier;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderBucket;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHangerSpawnEgg;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBag;

public final class ModItems {
    private static final List<Item> ENTRIES = new ArrayList<>();

    public static final Supplier<FieldGuide> FIELD_GUIDE = item("field_guide", FieldGuide::new);
    public static final Supplier<Item> RAW_DESERT_SKATE = food("raw_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.25F).build()));
    public static final Supplier<Item> COOKED_DESERT_SKATE = food("cooked_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.5F).build()));
    public static final Supplier<SpawnEggItem> DESERT_SKATE_SPAWN_EGG = item("desert_skate_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DESERT_SKATE.get())));
    public static final Supplier<BlockItem> VULTURE_POST = blockItem("vulture_post", ModBlocks.VULTURE_POST.get());
    public static final Supplier<CargoTongs> CARGO_TONGS = item("cargo_tongs",
            properties -> new CargoTongs(properties.stacksTo(1)));
    public static final Supplier<Item> CONDROC_FLIGHT_FEATHER = item("condroc_flight_feather", Item::new);
    public static final Supplier<LongFeatherArrowItem> LONG_FEATHER_ARROW = item("long_feather_arrow", LongFeatherArrowItem::new);
    public static final Supplier<SnowcacheFur> SNOWCACHE_FUR = item("snowcache_fur", SnowcacheFur::new);
    public static final Supplier<Item> SALVAGE_RAY_MOLT = item("salvage_ray_molt", Item::new);
    public static final Supplier<MailBag> MAIL_BAG = item("mail_bag",
            properties -> new MailBag(properties.stacksTo(1).fireResistant()));
    public static final Supplier<SpawnEggItem> VULTURE_SPAWN_EGG = item("condroc_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.VULTURE.get())));
    public static final Supplier<Item> ROCK_SILK = item("rock_silk", Item::new);
    public static final Supplier<Item> ROCK_SILK_SPOOL = item("rock_silk_spool", Item::new);
    public static final Supplier<SilkLauncher> SILK_LAUNCHER = item("silk_launcher",
            properties -> new SilkLauncher(properties.stacksTo(1)));
    public static final Supplier<RockHangerSpawnEgg> ROCK_HANGER_SPAWN_EGG = item("rock_hanger_spawn_egg",
            properties -> new RockHangerSpawnEgg(properties));
    public static final Supplier<BubbleSalamanderBucket> BUBBLE_SALAMANDER_BUCKET = item("bubble_salamander_bucket",
            properties -> new BubbleSalamanderBucket(properties.stacksTo(1)));
    public static final Supplier<SpawnEggItem> BUBBLE_SALAMANDER_SPAWN_EGG = item("bubble_salamander_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BUBBLE_SALAMANDER.get())));
    public static final Supplier<SpawnEggItem> VELOCIPOD_SPAWN_EGG = item("velocipod_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.VELOCIPOD.get())));
    public static final Supplier<SpawnEggItem> LUMEN_BUTTERFLY_SPAWN_EGG = item("lumen_butterfly_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(LumenButterflies.TYPE.get())));

    public static final Supplier<net.minecraft.world.item.BlockItem> KAVAT_INCUBATOR = item("kavat_incubator", properties -> new net.minecraft.world.item.BlockItem(ModBlocks.INCUBATOR.get(), properties.useBlockDescriptionPrefix()));
    public static final Supplier<net.minecraft.world.item.BlockItem> AIR_BLADDER = item("air_bladder", properties -> new net.minecraft.world.item.BlockItem(ModBlocks.AIR_BLADDER.get(), properties.useBlockDescriptionPrefix()) {
        @Override
        public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
            return AirBladderBlock.color(stack) == BubbleSalamanderColor.MIXED || super.isFoil(stack);
        }
    });
    public static final Supplier<Item> KAVAT_CODE = item("kavat_genetic_code", Item::new);
    public static final Supplier<Item> KAVAT_TEMPLATE = item("kavat_imprint", properties -> new Item(properties) {
        @Override public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
            return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                    ? super.getName(stack) : net.minecraft.network.chat.Component.translatable("kavat.blank_imprint");
        }
    });
    public static final Supplier<Item> KAVAT_EXTRACTOR = item("kavat_extractor", Item::new);
    public static final Supplier<SpawnEggItem> COLLECTOR_EGG = item("collector_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.COLLECTOR.get())));
    public static final Supplier<SpawnEggItem> KAVAT_EGG = item("kavat_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.KAVAT.get())));
    private ModItems() {
    }

    public static <T extends Item> Supplier<T> item(String name, Function<Item.Properties, T> factory) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, name);
        T item = Registry.register(BuiltInRegistries.ITEM, id, factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
        ENTRIES.add(item);
        return () -> item;
    }

    public static Supplier<BlockItem> blockItem(String name, Block block) {
        return item(name, properties -> new BlockItem(block, properties.useBlockDescriptionPrefix()));
    }

    private static Supplier<Item> food(String name, Item.Properties properties) {
        return item(name, ignored -> new Item(properties.setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModIdentity.ID, name)))));
    }

    public static void register() {
        DispenserBlock.registerProjectileBehavior(LONG_FEATHER_ARROW.get());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(ModIdentity.ID, "yagens_mobs"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.yagens_mobs"))
                        .icon(() -> COOKED_DESERT_SKATE.get().getDefaultInstance())
                        .displayItems((parameters, output) -> ENTRIES.stream()
                                .filter(item -> item != FIELD_GUIDE.get() || FieldGuide.isAvailable())
                                .sorted(Comparator.comparing(item -> item instanceof SpawnEggItem))
                                .forEach(output::accept))
                        .build());
    }
}

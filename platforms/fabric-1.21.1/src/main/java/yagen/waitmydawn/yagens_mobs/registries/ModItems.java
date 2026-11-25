package yagen.waitmydawn.yagens_mobs.registries;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderBucket;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHangerSpawnEgg;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBag;

public final class ModItems {
    private static final List<Item> ENTRIES = new ArrayList<>();

    public static final Supplier<Item> RAW_DESERT_SKATE = food("raw_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.25F).build()));
    public static final Supplier<Item> COOKED_DESERT_SKATE = food("cooked_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.5F).build()));
    public static final Supplier<SpawnEggItem> DESERT_SKATE_SPAWN_EGG = item("desert_skate_spawn_egg",
            () -> new SpawnEggItem(ModEntities.DESERT_SKATE.get(), 0xA86B37, 0x42766B, new Item.Properties()));
    public static final Supplier<BlockItem> VULTURE_POST = item("vulture_post", () -> new BlockItem(ModBlocks.VULTURE_POST.get(), new Item.Properties()));
    public static final Supplier<CargoTongs> CARGO_TONGS = item("cargo_tongs",
            () -> new CargoTongs(new Item.Properties().stacksTo(1)));
    public static final Supplier<Item> CONDROC_FLIGHT_FEATHER = item("condroc_flight_feather", () -> new Item(new Item.Properties()));
    public static final Supplier<MailBag> MAIL_BAG = item("mail_bag",
            () -> new MailBag(new Item.Properties().stacksTo(1).fireResistant()));
    public static final Supplier<SpawnEggItem> VULTURE_SPAWN_EGG = item("condroc_spawn_egg",
            () -> new SpawnEggItem(ModEntities.VULTURE.get(), 0x524839, 0xE0AE76, new Item.Properties()));
    public static final Supplier<Item> ROCK_SILK = item("rock_silk", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ROCK_SILK_SPOOL = item("rock_silk_spool", () -> new Item(new Item.Properties()));
    public static final Supplier<SilkLauncher> SILK_LAUNCHER = item("silk_launcher",
            () -> new SilkLauncher(new Item.Properties().stacksTo(1)));
    public static final Supplier<RockHangerSpawnEgg> ROCK_HANGER_SPAWN_EGG = item("rock_hanger_spawn_egg",
            () -> new RockHangerSpawnEgg(new Item.Properties()));
    public static final Supplier<BubbleSalamanderBucket> BUBBLE_SALAMANDER_BUCKET = item("bubble_salamander_bucket",
            () -> new BubbleSalamanderBucket(new Item.Properties().stacksTo(1)));
    public static final Supplier<SpawnEggItem> BUBBLE_SALAMANDER_SPAWN_EGG = item("bubble_salamander_spawn_egg",
            () -> new SpawnEggItem(ModEntities.BUBBLE_SALAMANDER.get(), 0x1E5054, 0x87C69B, new Item.Properties()));
    public static final Supplier<SpawnEggItem> VELOCIPOD_SPAWN_EGG = item("velocipod_spawn_egg",
            () -> new SpawnEggItem(ModEntities.VELOCIPOD.get(), 0x574659, 0x76A59A, new Item.Properties()));
    public static final Supplier<SpawnEggItem> LUMEN_BUTTERFLY_SPAWN_EGG = item("lumen_butterfly_spawn_egg",
            () -> new SpawnEggItem(LumenButterflies.TYPE.get(), 0x80542F, 0xEBDD76, new Item.Properties()));

    public static final Supplier<net.minecraft.world.item.BlockItem> KAVAT_INCUBATOR = item("kavat_incubator", () -> new net.minecraft.world.item.BlockItem(ModBlocks.INCUBATOR.get(), new Item.Properties()));
    public static final Supplier<Item> KAVAT_CODE = item("kavat_genetic_code", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> KAVAT_TEMPLATE = item("kavat_imprint", () -> new Item(new Item.Properties()) {
        @Override public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
            return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                    ? super.getName(stack) : net.minecraft.network.chat.Component.translatable("kavat.blank_imprint");
        }
    });
    public static final Supplier<Item> KAVAT_EXTRACTOR = item("kavat_extractor", () -> new Item(new Item.Properties()));
    public static final Supplier<SpawnEggItem> KAVAT_EGG = item("kavat_spawn_egg",
            () -> new SpawnEggItem(ModEntities.KAVAT.get(), 0x435563, 0xa6d7da, new Item.Properties()));
    private ModItems() {
    }

    public static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        var id = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
        T item = Registry.register(BuiltInRegistries.ITEM, id, factory.get());
        ENTRIES.add(item);
        return () -> item;
    }

    private static Supplier<Item> food(String name, Item.Properties properties) {
        return item(name, () -> new Item(properties));
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "yagens_mobs"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.yagens_mobs"))
                        .icon(() -> COOKED_DESERT_SKATE.get().getDefaultInstance())
                        .displayItems((parameters, output) -> ENTRIES.stream()
                                .sorted(Comparator.comparing(item -> item instanceof SpawnEggItem))
                                .forEach(output::accept))
                        .build());
    }
}

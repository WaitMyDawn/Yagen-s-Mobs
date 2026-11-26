package yagen.waitmydawn.yagens_mobs.registries;

import java.util.Comparator;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.guide.FieldGuide;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderBucket;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHangerSpawnEgg;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBag;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModIdentity.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModIdentity.ID);

    // Keep the item ID stable when a world temporarily runs without Patchouli.
    public static final DeferredItem<FieldGuide> FIELD_GUIDE = ITEMS.register("field_guide", FieldGuide::new);

    public static final DeferredItem<Item> RAW_DESERT_SKATE = ITEMS.registerSimpleItem("raw_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.25F).build()));
    public static final DeferredItem<Item> COOKED_DESERT_SKATE = ITEMS.registerSimpleItem("cooked_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.5F).build()));
    public static final DeferredItem<DeferredSpawnEggItem> DESERT_SKATE_SPAWN_EGG = ITEMS.register("desert_skate_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DESERT_SKATE, 0xA86B37, 0x42766B, new Item.Properties()));
    public static final DeferredItem<BlockItem> VULTURE_POST = ITEMS.registerSimpleBlockItem(ModBlocks.VULTURE_POST);
    public static final DeferredItem<CargoTongs> CARGO_TONGS = ITEMS.register("cargo_tongs",
            () -> new CargoTongs(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CONDROC_FLIGHT_FEATHER = ITEMS.registerSimpleItem("condroc_flight_feather");
    public static final DeferredItem<MailBag> MAIL_BAG = ITEMS.register("mail_bag",
            () -> new MailBag(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<DeferredSpawnEggItem> VULTURE_SPAWN_EGG = ITEMS.register("condroc_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VULTURE, 0x524839, 0xE0AE76, new Item.Properties()));
    public static final DeferredItem<Item> ROCK_SILK = ITEMS.registerSimpleItem("rock_silk");
    public static final DeferredItem<Item> ROCK_SILK_SPOOL = ITEMS.registerSimpleItem("rock_silk_spool");
    public static final DeferredItem<SilkLauncher> SILK_LAUNCHER = ITEMS.register("silk_launcher",
            () -> new SilkLauncher(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RockHangerSpawnEgg> ROCK_HANGER_SPAWN_EGG = ITEMS.register("rock_hanger_spawn_egg",
            () -> new RockHangerSpawnEgg(new Item.Properties()));
    public static final DeferredItem<BubbleSalamanderBucket> BUBBLE_SALAMANDER_BUCKET = ITEMS.register("bubble_salamander_bucket",
            () -> new BubbleSalamanderBucket(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DeferredSpawnEggItem> BUBBLE_SALAMANDER_SPAWN_EGG = ITEMS.register("bubble_salamander_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BUBBLE_SALAMANDER, 0x1E5054, 0x87C69B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VELOCIPOD_SPAWN_EGG = ITEMS.register("velocipod_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VELOCIPOD, 0x574659, 0x76A59A, new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YAGENS_MOBS = TABS.register("yagens_mobs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yagens_mobs"))
                    .icon(() -> COOKED_DESERT_SKATE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ITEMS.getEntries().stream()
                            .filter(entry -> entry != FIELD_GUIDE || FieldGuide.isAvailable())
                            .map(DeferredHolder::get)
                            .sorted(Comparator.comparing(item -> item instanceof SpawnEggItem))
                            .forEach(output::accept))
                    .build());

    public static final DeferredItem<BlockItem> KAVAT_INCUBATOR = ITEMS.registerSimpleBlockItem(ModBlocks.INCUBATOR);
    public static final DeferredItem<Item> KAVAT_CODE = ITEMS.registerSimpleItem("kavat_genetic_code");
    public static final DeferredItem<Item> KAVAT_TEMPLATE = ITEMS.register("kavat_imprint", () -> new Item(new Item.Properties()) {
        @Override public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
            return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                    ? super.getName(stack) : net.minecraft.network.chat.Component.translatable("kavat.blank_imprint");
        }
    });
    public static final DeferredItem<Item> KAVAT_EXTRACTOR = ITEMS.registerSimpleItem("kavat_extractor");
    public static final DeferredItem<DeferredSpawnEggItem> KAVAT_EGG = ITEMS.register("kavat_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KAVAT, 0x435563, 0xa6d7da, new Item.Properties()));
    private ModItems() {
    }
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}

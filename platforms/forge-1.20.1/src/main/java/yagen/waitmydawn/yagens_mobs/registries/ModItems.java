package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import java.util.Map;
import java.util.EnumMap;
import yagen.waitmydawn.yagens_mobs.entity.vulture.LongFeatherArrowItem;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheFur;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderItem;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderVariants;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;
import java.util.Comparator;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderBucket;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHangerSpawnEgg;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBag;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModIdentity.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModIdentity.ID);

    public static final RegistryObject<yagen.waitmydawn.yagens_mobs.guide.FieldGuide> FIELD_GUIDE = ITEMS.register("field_guide", yagen.waitmydawn.yagens_mobs.guide.FieldGuide::new);
    public static final RegistryObject<Item> RAW_DESERT_SKATE = ITEMS.register("raw_desert_skate",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.25F).build())));
    public static final RegistryObject<Item> COOKED_DESERT_SKATE = ITEMS.register("cooked_desert_skate",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(12).saturationMod(0.5F).build())));
    public static final RegistryObject<ForgeSpawnEggItem> DESERT_SKATE_SPAWN_EGG = ITEMS.register("desert_skate_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DESERT_SKATE, 0xA86B37, 0x42766B, new Item.Properties()));
    public static final RegistryObject<BlockItem> VULTURE_POST = ITEMS.register("vulture_post", () -> new BlockItem(ModBlocks.VULTURE_POST.get(), new Item.Properties()));
    public static final RegistryObject<CargoTongs> CARGO_TONGS = ITEMS.register("cargo_tongs",
            () -> new CargoTongs(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CONDROC_FLIGHT_FEATHER = ITEMS.register("condroc_flight_feather", () -> new Item(new Item.Properties()));
    public static final RegistryObject<LongFeatherArrowItem> LONG_FEATHER_ARROW = ITEMS.register("long_feather_arrow",
            () -> new LongFeatherArrowItem(new Item.Properties()));
    public static final RegistryObject<SnowcacheFur> SNOWCACHE_FUR = ITEMS.register("snowcache_fur",
            () -> new SnowcacheFur(new Item.Properties()));
    public static final RegistryObject<Item> SALVAGE_RAY_MOLT = ITEMS.register("salvage_ray_molt", () -> new Item(new Item.Properties()));
    public static final RegistryObject<MailBag> MAIL_BAG = ITEMS.register("mail_bag",
            () -> new MailBag(new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<ForgeSpawnEggItem> VULTURE_SPAWN_EGG = ITEMS.register("condroc_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.VULTURE, 0x524839, 0xE0AE76, new Item.Properties()));
    public static final RegistryObject<Item> ROCK_SILK = ITEMS.register("rock_silk", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ROCK_SILK_SPOOL = ITEMS.register("rock_silk_spool", () -> new Item(new Item.Properties()));
    public static final RegistryObject<SilkLauncher> SILK_LAUNCHER = ITEMS.register("silk_launcher",
            () -> new SilkLauncher(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<RockHangerSpawnEgg> ROCK_HANGER_SPAWN_EGG = ITEMS.register("rock_hanger_spawn_egg",
            () -> new RockHangerSpawnEgg(new Item.Properties()));
    public static final RegistryObject<BubbleSalamanderBucket> BUBBLE_SALAMANDER_BUCKET = ITEMS.register("bubble_salamander_bucket",
            () -> new BubbleSalamanderBucket(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ForgeSpawnEggItem> BUBBLE_SALAMANDER_SPAWN_EGG = ITEMS.register("bubble_salamander_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BUBBLE_SALAMANDER, 0x1E5054, 0x87C69B, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> VELOCIPOD_SPAWN_EGG = ITEMS.register("velocipod_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.VELOCIPOD, 0x574659, 0x76A59A, new Item.Properties()));
    public static final RegistryObject<BlockItem> SALVAGE_NEST_BOX = ITEMS.register("salvage_nest_box",
            () -> new BlockItem(yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays.NEST_BOX.get(), new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> SALVAGE_RAY_SPAWN_EGG = ITEMS.register("salvage_ray_spawn_egg",
            () -> new ForgeSpawnEggItem(yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays.RAY, 0x2b2138, 0x987BA7, new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> YAGENS_MOBS = TABS.register("yagens_mobs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yagens_mobs"))
                    .icon(() -> COOKED_DESERT_SKATE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ITEMS.getEntries().stream()
                            .map(RegistryObject::get)
                            .filter(item -> item != ModItems.AIR_BLADDER.get())
                            .filter(item -> item != FIELD_GUIDE.get() || yagen.waitmydawn.yagens_mobs.guide.FieldGuide.isAvailable())
                            .sorted(Comparator.comparing(item -> item instanceof SpawnEggItem))
                            .forEach(output::accept))
                    .build());

    public static final RegistryObject<BlockItem> KAVAT_INCUBATOR = ITEMS.register("kavat_incubator", () -> new BlockItem(ModBlocks.INCUBATOR.get(),new Item.Properties()));
    public static final RegistryObject<BlockItem> AIR_BLADDER = ITEMS.register("air_bladder",
            () -> new AirBladderItem(ModBlocks.AIR_BLADDER.get(), new Item.Properties()));
    public static final Map<BubbleSalamanderColor, Supplier<BlockItem>> AIR_BLADDERS = registerBladders();

    private static Map<BubbleSalamanderColor, Supplier<BlockItem>> registerBladders() {
        var variants = new EnumMap<BubbleSalamanderColor, Supplier<BlockItem>>(BubbleSalamanderColor.class);
        for (var color : BubbleSalamanderColor.values()) {
            variants.put(color, ITEMS.register(AirBladderVariants.path(color),
                    () -> new AirBladderItem(ModBlocks.AIR_BLADDERS.get(color).get(), new Item.Properties())));
        }
        return java.util.Collections.unmodifiableMap(variants);
    }
    public static final RegistryObject<Item> KAVAT_CODE = ITEMS.register("kavat_genetic_code", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KAVAT_TEMPLATE = ITEMS.register("kavat_imprint", () -> new Item(new Item.Properties()) {
        @Override public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
            return stack.getTag() != null && stack.getTag().contains("Traits")
                    ? super.getName(stack) : net.minecraft.network.chat.Component.translatable("kavat.blank_imprint");
        }
    });
    public static final RegistryObject<Item> KAVAT_EXTRACTOR = ITEMS.register("kavat_extractor", () -> new Item(new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> COLLECTOR_EGG = ITEMS.register("collector_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.COLLECTOR, 0x978883, 0x78904c, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> KAVAT_EGG = ITEMS.register("kavat_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KAVAT, 0x435563, 0xa6d7da, new Item.Properties()));
    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> LONG_FEATHER_ARROW.get().registerDispenser()));
    }
}

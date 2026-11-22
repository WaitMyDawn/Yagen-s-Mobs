package yagen.waitmydawn.yagens_mobs.registries;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
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
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamanderBucket;
import yagen.waitmydawn.yagens_mobs.entity.RockHangerSpawnEgg;
import yagen.waitmydawn.yagens_mobs.silk.SilkLauncher;
import yagen.waitmydawn.yagens_mobs.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.transport.MailBag;

public final class ModItems {
    private static final List<Item> ENTRIES = new ArrayList<>();

    public static final Supplier<Item> RAW_DESERT_SKATE = food("raw_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.25F).build()));
    public static final Supplier<Item> COOKED_DESERT_SKATE = food("cooked_desert_skate",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.5F).build()));
    public static final Supplier<SpawnEggItem> DESERT_SKATE_SPAWN_EGG = item("desert_skate_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DESERT_SKATE.get())));
    public static final Supplier<BlockItem> VULTURE_POST = item("vulture_post", properties -> new BlockItem(ModBlocks.VULTURE_POST.get(), properties));
    public static final Supplier<CargoTongs> CARGO_TONGS = item("cargo_tongs",
            properties -> new CargoTongs(properties.stacksTo(1)));
    public static final Supplier<Item> CONDROC_FLIGHT_FEATHER = item("condroc_flight_feather", Item::new);
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
    private ModItems() {
    }

    private static <T extends Item> Supplier<T> item(String name, Function<Item.Properties, T> factory) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, name);
        T item = Registry.register(BuiltInRegistries.ITEM, id, factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
        ENTRIES.add(item);
        return () -> item;
    }

    private static Supplier<Item> food(String name, Item.Properties properties) {
        return item(name, ignored -> new Item(properties.setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModIdentity.ID, name)))));
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(ModIdentity.ID, "yagens_mobs"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.yagens_mobs"))
                        .icon(() -> COOKED_DESERT_SKATE.get().getDefaultInstance())
                        .displayItems((parameters, output) -> ENTRIES.stream()
                                .sorted(Comparator.comparing(item -> item instanceof SpawnEggItem))
                                .forEach(output::accept))
                        .build());
    }
}

package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ModTags {
    public static final TagKey<Item> BUBBLE_SALAMANDER_FOOD = TagKey.create(Registries.ITEM, id("bubble_salamander_food"));
    public static final TagKey<EntityType<?>> BUBBLE_SALAMANDER_PREY = TagKey.create(Registries.ENTITY_TYPE, id("bubble_salamander_prey"));
    public static final TagKey<Biome> BUBBLE_SALAMANDER_BREEDING_EXCLUDED =
            TagKey.create(Registries.BIOME, id("bubble_salamander_breeding_excluded"));
    public static final TagKey<EntityType<?>> ROCK_HANGER_PREY = TagKey.create(Registries.ENTITY_TYPE, id("rock_hanger_prey"));
    public static final TagKey<Item> ROCK_HANGER_BAIT = TagKey.create(Registries.ITEM, id("rock_hanger_bait"));
    public static final TagKey<Block> BURROWABLE_SAND = TagKey.create(Registries.BLOCK, id("desert_skate_burrowable"));
    public static final TagKey<Item> SKATE_BAIT = TagKey.create(Registries.ITEM, id("desert_skate_bait"));
    public static final TagKey<Item> VULTURE_FOOD = TagKey.create(Registries.ITEM, id("condroc_food"));
    public static final TagKey<Biome> VULTURE_HABITAT = TagKey.create(Registries.BIOME, id("condroc_habitat"));
    public static final TagKey<Biome> VULTURE_RAVINE_EXCLUSIONS = TagKey.create(Registries.BIOME, id("condroc_ravine_exclusions"));
    public static final TagKey<EntityType<?>> SKATE_PREY = TagKey.create(Registries.ENTITY_TYPE, id("desert_skate_prey"));
    public static final TagKey<Item> KAVAT_RAW_MEAT = TagKey.create(Registries.ITEM, id("kavat_raw_meat"));
    public static final TagKey<Item> KAVAT_COOKED_MEAT = TagKey.create(Registries.ITEM, id("kavat_cooked_meat"));

    private ModTags() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}

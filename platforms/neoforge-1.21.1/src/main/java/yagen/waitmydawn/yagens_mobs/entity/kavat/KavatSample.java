package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public record KavatSample(KavatGenes genes, int generation, UUID owner) {
    static final int[] COLORS = {0xbe8054, 0x9ba5ac, 0xd4a34d, 0x589bad, 0x69a786, 0x768ab5, 0xbd7068, 0xeeeeea,
            0x655b74, 0xb974b8, 0xd7ccd9, 0xdf9aa3, 0xdc5532, 0xf4a756, 0xedbecb, 0xdbbc75};
    static final List<Item> MATERIALS = List.of(Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND,
            Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE, Items.SNOW_BLOCK, Items.WITHER_ROSE, Items.DRAGON_BREATH,
            Items.CHORUS_FLOWER, Items.SPORE_BLOSSOM, Items.MAGMA_BLOCK, Items.SHROOMLIGHT, Items.CHERRY_LOG, Items.SAND);
    static Component trait(String id) {
        return Component.translatable("kavat.trait." + id);
    }
    public Component description() {
        return Component.empty()
                .append(trait(KavatGenes.BREEDS.get(genes.breed())))
                .append(" · ")
                .append(trait(KavatGenes.COATS.get(genes.coat())))
                .append(" · ")
                .append(trait(KavatGenes.EARS.get(genes.ear())))
                .append(" · ")
                .append(trait(KavatGenes.TAILS.get(genes.tail())));
    }
    public ItemStack stack(boolean imprint, int count) {
        ItemStack stack = new ItemStack(imprint ? ModItems.KAVAT_TEMPLATE.get() : ModItems.KAVAT_CODE.get(), count);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Traits", genes.packed());
        if (imprint) {
            tag.putInt("Generation", generation);
            tag.putUUID("Owner", owner);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE,
                new ItemLore(imprint ? List.of(description(), Component.translatable("kavat.generation", generation),
                                               Component.translatable("kavat.owner", owner.toString()))
                                     : List.of(description())));
        return stack;
    }
    public static KavatSample read(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null)
            return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("Traits", 3) || stack.is(ModItems.KAVAT_TEMPLATE.get()) && !tag.hasUUID("Owner"))
            return null;
        try {
            return new KavatSample(KavatGenes.unpack(tag.getInt("Traits")),
                    Math.clamp(tag.getInt("Generation"), 0, 100), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}

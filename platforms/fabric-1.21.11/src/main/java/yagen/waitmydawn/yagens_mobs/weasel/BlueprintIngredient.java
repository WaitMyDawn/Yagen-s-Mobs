package yagen.waitmydawn.yagens_mobs.weasel;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

public record BlueprintIngredient(@Nullable Ingredient ingredient) {
    private static final ItemStack[] NO_ITEMS = new ItemStack[0];

    public boolean isEmpty() {
        return ingredient == null;
    }

    public boolean test(ItemStack stack) {
        return ingredient != null && ingredient.test(stack);
    }

    public ItemStack[] getItems() {
        return ingredient == null ? NO_ITEMS : ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }
}

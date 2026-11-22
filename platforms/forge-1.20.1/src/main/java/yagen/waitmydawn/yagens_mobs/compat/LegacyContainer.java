package yagen.waitmydawn.yagens_mobs.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class LegacyContainer extends SimpleContainer {
    private boolean loading;

    public LegacyContainer(int size) {
        super(size);
    }

    public NonNullList<ItemStack> getItems() {
        var inventory = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int index = 0; index < getContainerSize(); index++) {
            inventory.set(index, getItem(index));
        }
        return inventory;
    }

    public void applyItems(NonNullList<ItemStack> items) {
        loading = true;
        try {
            for (int index = 0; index < getContainerSize(); index++) {
                setItem(index, items.get(index));
            }
        } finally {
            loading = false;
        }
    }

    public void loadFrom(ItemStack owner) {
        var contents = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        LegacyItemData.loadItems(owner, contents);
        applyItems(contents);
    }

    public boolean isLoading() {
        return loading;
    }
}

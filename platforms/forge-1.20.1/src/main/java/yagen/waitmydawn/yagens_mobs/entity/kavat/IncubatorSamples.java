package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

final class IncubatorSamples extends SimpleContainer {
    IncubatorSamples() { super(10); }

    @Override public int getMaxStackSize() { return 1; }

    NonNullList<ItemStack> snapshot() {
        var result = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < result.size(); i++) result.set(i, getItem(i));
        return result;
    }

    void loadSamples(CompoundTag tag) {
        var stored = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, stored);
        for (int i = 0; i < stored.size(); i++) setItem(i, stored.get(i));
    }

    boolean hasImprint() {
        for (int i = 0; i < getContainerSize(); i++)
            if (getItem(i).is(ModItems.KAVAT_TEMPLATE.get())) return true;
        return false;
    }

    boolean visibleSample(int index) {
        if (!hasImprint() || !getItem(index).isEmpty()) return true;
        int first = -1;
        for (int i = 0; i < 10; i++) {
            if (getItem(i).isEmpty()) continue;
            if (first >= 0) return false;
            first = i;
        }
        return index == (first % 5 == 4 ? first - 1 : first + 1);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!visibleSample(slot)) return false;
        if (!stack.is(ModItems.KAVAT_CODE.get()) && !stack.is(ModItems.KAVAT_TEMPLATE.get())) return false;
        if (KavatSample.read(stack) == null) return false;
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack other = getItem(i);
            if (!other.isEmpty() && !other.is(stack.getItem())) return false;
        }
        return true;
    }

    boolean ready() {
        int occupied = 0;
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) occupied++;
        return occupied == (hasImprint() ? 2 : 10);
    }
}

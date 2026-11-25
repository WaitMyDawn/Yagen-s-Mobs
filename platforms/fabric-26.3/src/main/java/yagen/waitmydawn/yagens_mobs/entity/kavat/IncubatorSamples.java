package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

final class IncubatorSamples extends SimpleContainer {
    private final Runnable changed;
    IncubatorSamples(Runnable changed) {
        super(10);
        this.changed = changed;
    }
    @Override public void setChanged() { changed.run(); }

    @Override public int getMaxStackSize() { return 1; }

    boolean hasImprint() {
        return getItems().stream().anyMatch(s -> s.is(ModItems.KAVAT_TEMPLATE.get()));
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
        for (var other : getItems())
            if (!other.isEmpty() && !other.is(stack.getItem())) return false;
        return true;
    }

    boolean ready() {
        int occupied = 0;
        for (var s : getItems()) if (!s.isEmpty()) occupied++;
        return occupied == (hasImprint() ? 2 : 10);
    }
}

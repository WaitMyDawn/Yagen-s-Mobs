package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

interface WeaselInventory {
    WeaselInventory UNAVAILABLE = new WeaselInventory() {
        @Override public int size() { return 0; }
        @Override public ItemStack peek(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }
    };

    int size();
    default boolean awaitsSupply() { return false; }
    ItemStack peek(int slot);
    ItemStack take(int slot, int amount, boolean simulate);
    int insert(int slot, ItemStack stack, int amount, boolean simulate);

    default void addStock(WeaselStock stock, Set<Item> relevant) {
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = peek(slot);
            if (relevant.isEmpty() || relevant.contains(stack.getItem())) {
                stock.add(take(slot, stack.getCount(), true));
            }
        }
    }

    default boolean hasMaterial(WeaselStock pulls) {
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = peek(slot);
            int amount = (int) Math.min(stack.getCount(), pulls.count(stack));
            if (amount > 0 && !take(slot, amount, true).isEmpty()) return true;
        }
        return false;
    }

    default boolean collect(WeaselStock pulls, WeaselStock cargo) {
        boolean changed = false;
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = peek(slot);
            int amount = (int) Math.min(stack.getCount(), pulls.count(stack));
            if (amount <= 0) continue;
            ItemStack taken = take(slot, amount, false);
            if (!taken.isEmpty()) {
                pulls.remove(taken, taken.getCount());
                cargo.add(taken);
                changed = true;
            }
        }
        return changed;
    }

    default int insert(ItemStack stack, boolean simulate) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < size() && remaining > 0; slot++) {
            remaining -= insert(slot, stack, remaining, simulate);
        }
        return stack.getCount() - remaining;
    }

    record Vanilla(Container container) implements WeaselInventory {
        @Override public int size() { return container.getContainerSize(); }
        @Override public ItemStack peek(int slot) { return container.getItem(slot); }

        @Override
        public ItemStack take(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            ItemStack existing = peek(slot);
            return simulate ? existing.copyWithCount(Math.min(amount, existing.getCount()))
                    : container.removeItem(slot, amount);
        }

        @Override
        public int insert(int slot, ItemStack stack, int amount, boolean simulate) {
            if (amount <= 0 || !container.canPlaceItem(slot, stack)) return 0;
            ItemStack existing = peek(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) return 0;
            int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int accepted = Math.min(amount, Math.max(0, limit - existing.getCount()));
            if (accepted > 0 && !simulate) {
                if (existing.isEmpty()) container.setItem(slot, stack.copyWithCount(accepted));
                else existing.grow(accepted);
                container.setChanged();
            }
            return accepted;
        }
    }
}

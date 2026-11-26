package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SalvageCargo {
    private static final int CAPACITY = 64;
    private final List<ItemStack> stacks = new ArrayList<>();

    boolean isEmpty() { return stacks.isEmpty(); }

    boolean hasRoom() { return remaining() > 0; }

    ItemStack display() { return isEmpty() ? ItemStack.EMPTY : stacks.get(0).copy(); }

    List<ItemStack> stacks() { return List.copyOf(stacks); }

    void clear() { stacks.clear(); }

    private int remaining() { return CAPACITY - stacks.stream().mapToInt(ItemStack::getCount).sum(); }

    boolean take(ItemEntity entity) {
        ItemStack left = entity.getItem().copy();
        int count = Math.min(remaining(), left.getCount());
        if (count <= 0 || !entity.isAlive()) {
            return false;
        }
        add(left.split(count));
        if (left.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(left);
        }
        return true;
    }

    private void add(ItemStack stack) {
        for (ItemStack existing : stacks) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                int merged = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(merged);
                stack.shrink(merged);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        while (!stack.isEmpty()) {
            stacks.add(stack.split(stack.getMaxStackSize()));
        }
    }

    boolean eatFruit() {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack item = stacks.get(i);
            if (item.is(Items.CHORUS_FRUIT)) {
                item.shrink(1);
                if (item.isEmpty()) {
                    stacks.remove(i);
                }
                return true;
            }
        }
        return false;
    }

    ListTag save() {
        var tag = new ListTag();
        for (ItemStack stack : stacks) {
            tag.add(stack.save(new CompoundTag()));
        }
        return tag;
    }

    void read(CompoundTag tag) {
        clear();
        if (!tag.contains("CargoStacks", Tag.TAG_LIST)) {
            ItemStack legacy = ItemStack.of( tag.getCompound("Cargo"));
            add(legacy.copyWithCount(Math.min(CAPACITY, legacy.getCount())));
            return;
        }
        for (Tag row : tag.getList("CargoStacks", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of( (CompoundTag) row);
            add(stack.copyWithCount(Math.min(remaining(), stack.getCount())));
        }
    }
}

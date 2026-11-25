package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
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

    ItemStack display() { return isEmpty() ? ItemStack.EMPTY : stacks.getFirst().copy(); }

    List<ItemStack> stacks() { return List.copyOf(stacks); }

    void clear() { stacks.clear(); }

    private int remaining() { return CAPACITY - stacks.stream().mapToInt(ItemStack::getCount).sum(); }

    void take(ItemEntity entity) {
        ItemStack left = entity.getItem().copy();
        int count = Math.min(remaining(), left.getCount());
        if (count <= 0 || !entity.isAlive()) {
            return;
        }
        add(left.split(count));
        if (left.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(left);
        }
    }

    private void add(ItemStack stack) {
        for (ItemStack existing : stacks) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
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

    ListTag save(HolderLookup.Provider registries) {
        var tag = new ListTag();
        for (ItemStack stack : stacks) {
            tag.add(SaveTag.saveItem(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack));
        }
        return tag;
    }

    private static ItemStack parse(HolderLookup.Provider registries, Tag tag) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        clear();
        if (!tag.getList("CargoStacks").isPresent()) {
            ItemStack legacy = parse(registries, tag.getCompoundOrEmpty("Cargo"));
            add(legacy.copyWithCount(Math.min(CAPACITY, legacy.getCount())));
            return;
        }
        for (Tag row : tag.getListOrEmpty("CargoStacks")) {
            ItemStack stack = parse(registries, row);
            add(stack.copyWithCount(Math.min(remaining(), stack.getCount())));
        }
    }
}

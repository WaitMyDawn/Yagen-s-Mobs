package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class WeaselStock {
    private static final class Entry {
        final ItemStack key;
        long count;

        Entry(ItemStack key, long count) {
            this.key = key;
            this.count = count;
        }
    }

    private final Map<Item, List<Entry>> items = new LinkedHashMap<>();

    boolean isEmpty() { return items.isEmpty(); }

    ItemStack display() {
        return isEmpty() ? ItemStack.EMPTY : items.values().iterator().next().getFirst().key.copy();
    }

    long count(ItemStack stack) {
        var variants = items.get(stack.getItem());
        if (variants != null) {
            for (Entry entry : variants) {
                if (ItemStack.isSameItemSameComponents(entry.key, stack)) return entry.count;
            }
        }
        return 0;
    }

    long count(Item item, Predicate<ItemStack> test) {
        var variants = items.get(item);
        if (variants == null) return 0;
        long count = 0;
        for (Entry entry : variants) if (test.test(entry.key)) count += entry.count;
        return count;
    }

    List<ItemStack> variants(Item item) {
        var variants = items.get(item);
        return variants == null ? List.of() : variants.stream().map(entry -> entry.key).toList();
    }

    void add(ItemStack stack) { add(stack, stack.getCount()); }

    void add(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        var variants = items.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>());
        for (Entry entry : variants) {
            if (ItemStack.isSameItemSameComponents(entry.key, stack)) {
                entry.count += count;
                return;
            }
        }
        variants.add(new Entry(stack.copyWithCount(1), count));
    }

    long remove(ItemStack stack, long count) {
        var variants = items.get(stack.getItem());
        if (variants == null || count <= 0) return 0;
        for (int i = 0; i < variants.size(); i++) {
            Entry entry = variants.get(i);
            if (!ItemStack.isSameItemSameComponents(entry.key, stack)) continue;
            long amount = Math.min(count, entry.count);
            entry.count -= amount;
            if (entry.count == 0) {
                variants.remove(i);
                if (variants.isEmpty()) items.remove(stack.getItem());
            }
            return amount;
        }
        return 0;
    }

    void moveTo(WeaselStock target) {
        items.values().forEach(variants -> variants.forEach(entry -> target.add(entry.key, entry.count)));
        items.clear();
    }

    List<ItemStack> preview(int maxStacks) {
        List<ItemStack> stacks = new ArrayList<>();
        for (var variants : items.values()) {
            for (var entry : variants) {
                long remaining = entry.count;
                while (remaining > 0 && stacks.size() < maxStacks) {
                    int count = (int) Math.min(remaining, entry.key.getMaxStackSize());
                    stacks.add(entry.key.copyWithCount(count));
                    remaining -= count;
                }
                if (stacks.size() == maxStacks) return stacks;
            }
        }
        return stacks;
    }

    int give(ServerPlayer player) {
        int result = 0;
        var groups = items.values().iterator();
        while (groups.hasNext()) {
            var variants = groups.next().iterator();
            while (variants.hasNext()) {
                var entry = variants.next();
                while (entry.count > 0) {
                    int count = (int) Math.min(entry.count, entry.key.getMaxStackSize());
                    ItemStack transfer = entry.key.copyWithCount(count);
                    int received = giveStack(player, transfer);
                    entry.count -= received;
                    result += received;
                    if (received < count) break;
                }
                if (entry.count == 0) variants.remove();
            }
        }
        items.values().removeIf(List::isEmpty);
        return result;
    }

    static int giveStack(ServerPlayer player, ItemStack transfer) {
        var inventory = player.getInventory();
        int count = transfer.getCount();
        while (!transfer.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(transfer);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) break;
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                inventory.setItem(slot, transfer.split(transfer.getMaxStackSize()));
            } else {
                existing.grow(transfer.split(existing.getMaxStackSize() - existing.getCount()).getCount());
            }
        }
        if (transfer.getCount() != count) inventory.setChanged();
        return count - transfer.getCount();
    }

    void drop(net.minecraft.server.level.ServerLevel level, SnowcacheWeasel mob) {
        for (var variants : items.values()) {
            for (var entry : variants) {
                while (entry.count > 0) {
                    int count = (int) Math.min(entry.count, entry.key.getMaxStackSize());
                    mob.spawnAtLocation(level, entry.key.copyWithCount(count));
                    entry.count -= count;
                }
            }
        }
        items.clear();
    }

    static Tag saveStack(HolderLookup.Provider lookup, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
    }

    static java.util.Optional<ItemStack> readStack(HolderLookup.Provider lookup, Tag tag) {
        return ItemStack.CODEC.parse(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).result();
    }

    ListTag save(HolderLookup.Provider lookup) {
        var list = new ListTag();
        for (var variants : items.values()) {
            for (var entry : variants) {
                var tag = new CompoundTag();
                tag.put("Stack", saveStack(lookup, entry.key));
                tag.putLong("Quantity", entry.count);
                list.add(tag);
            }
        }
        return list;
    }

    void read(HolderLookup.Provider lookup, ListTag list) {
        items.clear();
        for (Tag row : list) {
            if (!(row instanceof CompoundTag tag)) continue;
            readStack(lookup, tag.getCompoundOrEmpty("Stack")).ifPresent(stack -> add(stack, tag.getLongOr("Quantity", 0L)));
        }
    }
}


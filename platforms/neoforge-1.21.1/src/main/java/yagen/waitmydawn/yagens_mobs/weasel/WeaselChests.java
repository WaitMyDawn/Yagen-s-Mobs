package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

final class WeaselChests {
    static final int RANGE = 16;

    enum Mode {
        BOTH, DEPOSIT, TAKE;

        boolean deposit() { return this != TAKE; }
        boolean take() { return this != DEPOSIT; }
        Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    record Site(BlockPos pos, Container items) {
        int first() {
            for (int slot = 0; slot < items.getContainerSize(); slot++) {
                if (!items.getItem(slot).isEmpty()) {
                    return slot;
                }
            }
            return -1;
        }

        @Nullable ResourceLocation category() {
            int first = first();
            return first < 0 ? null : WeaselTabs.of(items.getItem(first));
        }

        int room(ItemStack stack, boolean fallback) {
            int room = 0;
            int first = first();
            boolean allowBeforeAnchor = fallback && first >= 0 && WeaselTabs.of(stack).equals(category());
            for (int slot = 0; slot < items.getContainerSize(); slot++) {
                ItemStack existing = items.getItem(slot);
                if (items.canPlaceItem(slot, stack)) {
                    if (existing.isEmpty() && (slot > first || allowBeforeAnchor)) {
                        room += Math.min(stack.getMaxStackSize(), items.getMaxStackSize());
                    } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                        room += Math.max(0, Math.min(stack.getMaxStackSize(), items.getMaxStackSize()) - existing.getCount());
                    }
                }
            }
            return room;
        }

        int deposit(ItemStack stack, boolean fallback) {
            int before = stack.getCount();
            int first = first();
            int last = items.getContainerSize() - 1;
            while (last >= 0 && items.getItem(last).isEmpty()) {
                last--;
            }
            if (!fallback) {
                for (int slot = Math.max(first, 0); slot <= last && !stack.isEmpty(); slot++) {
                    ItemStack existing = items.getItem(slot);
                    if (items.canPlaceItem(slot, stack) && ItemStack.isSameItemSameComponents(existing, stack)) {
                        existing.grow(stack.split(Math.max(0, Math.min(stack.getMaxStackSize(), items.getMaxStackSize()) - existing.getCount())).getCount());
                    }
                }
                for (int slot = first + 1; slot < items.getContainerSize() && !stack.isEmpty(); slot++) {
                    insertEmpty(slot, stack);
                }
            } else {
                // Preserve the classification item and append to the occupied part before reusing holes.
                for (int slot = last + 1; slot < items.getContainerSize() && !stack.isEmpty(); slot++) {
                    insertEmpty(slot, stack);
                }
                for (int slot = last; slot > first && !stack.isEmpty(); slot--) {
                    insertEmpty(slot, stack);
                }
                for (int slot = last; slot >= Math.max(first, 0) && !stack.isEmpty(); slot--) {
                    ItemStack existing = items.getItem(slot);
                    if (items.canPlaceItem(slot, stack) && ItemStack.isSameItemSameComponents(existing, stack)) {
                        existing.grow(stack.split(Math.max(0, Math.min(stack.getMaxStackSize(), items.getMaxStackSize()) - existing.getCount())).getCount());
                    }
                }
                // Empty slots in front of the anchor can be used by a matching stack without changing the tab.
                for (int slot = first - 1; slot >= 0 && !stack.isEmpty() && WeaselTabs.of(stack).equals(category()); slot--) {
                    insertEmpty(slot, stack);
                }
            }
            if (stack.getCount() != before) {
                items.setChanged();
            }
            return before - stack.getCount();
        }

        private void insertEmpty(int slot, ItemStack stack) {
            if (items.getItem(slot).isEmpty() && items.canPlaceItem(slot, stack)) {
                items.setItem(slot, stack.split(Math.min(stack.getMaxStackSize(), items.getMaxStackSize())));
            }
        }

        boolean fitsAtEnd(List<ItemStack> cargo) {
            int first = first();
            ResourceLocation tab = category();
            var counts = new int[items.getContainerSize()];
            var stacks = new ItemStack[counts.length];
            int last = -1;
            for (int i = 0; i < counts.length; i++) {
                stacks[i] = items.getItem(i);
                counts[i] = stacks[i].getCount();
                if (counts[i] > 0) last = i;
            }
            for (ItemStack stack : cargo) {
                int remaining = stack.getCount();
                int limit = Math.min(stack.getMaxStackSize(), items.getMaxStackSize());
                int tail = last;
                // Match the insertion order so accepting a full batch does not depend on a different slot layout.
                for (int i = tail + 1; i < counts.length && remaining > 0; i++) {
                    if (counts[i] == 0 && items.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit);
                        stacks[i] = stack;
                        counts[i] = moved;
                        remaining -= moved;
                        last = i;
                    }
                }
                for (int i = tail; i > first && remaining > 0; i--) {
                    if (counts[i] == 0 && items.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit);
                        stacks[i] = stack;
                        counts[i] = moved;
                        remaining -= moved;
                    }
                }
                for (int i = tail; i >= Math.max(first, 0) && remaining > 0; i--) {
                    if (counts[i] < limit && ItemStack.isSameItemSameComponents(stacks[i], stack) && items.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit - counts[i]);
                        counts[i] += moved;
                        remaining -= moved;
                    }
                }
                for (int i = first - 1; i >= 0 && remaining > 0; i--) {
                    if (counts[i] == 0 && WeaselTabs.of(stack).equals(tab) && items.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit);
                        stacks[i] = stack;
                        counts[i] = moved;
                        remaining -= moved;
                    }
                }
                if (remaining > 0) return false;
                if (first < 0) {
                    first = 0;
                    while (first < counts.length && counts[first] == 0) first++;
                    if (first < counts.length) tab = WeaselTabs.of(stacks[first]);
                }
            }
            return true;
        }
    }

    private WeaselChests() {}

    static @Nullable Site at(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (!level.hasChunkAt(pos) || !level.mayInteract(owner, pos)) {
            return null;
        }
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity block)
                || block.getLootTable() != null || !unlocked(block, owner)) {
            return null;
        }
        BlockPos canonical = pos.immutable();
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos paired = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!level.hasChunkAt(paired) || !level.mayInteract(owner, paired)
                    || !(level.getBlockEntity(paired) instanceof ChestBlockEntity other)
                    || other.getLootTable() != null || !unlocked(other, owner)) {
                return null;
            }
            if (paired.asLong() < pos.asLong()) {
                canonical = paired;
            }
        }
        Container inventory = ChestBlock.getContainer(chest, state, level, pos, false);
        return inventory == null ? null : new Site(canonical, inventory);
    }

    private static boolean unlocked(ChestBlockEntity chest, ServerPlayer owner) {
        return ((yagen.waitmydawn.yagens_mobs.weasel.mixin.WeaselChestLock) chest).yagensMobs$lock().unlocksWith(owner.getMainHandItem());
    }

    static List<BlockPos> discover(ServerLevel level, BlockPos center, ServerPlayer player) {
        int x0 = (center.getX() - RANGE) >> 4;
        int x1 = (center.getX() + RANGE) >> 4;
        int z0 = (center.getZ() - RANGE) >> 4;
        int z1 = (center.getZ() + RANGE) >> 4;
        Set<BlockPos> found = new java.util.HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.distSqr(center) <= RANGE * RANGE && entry.getValue() instanceof ChestBlockEntity) {
                        var site = at(level, pos, player);
                        if (site != null) {
                            found.add(site.pos());
                        }
                    }
                }
            }
        }
        return found.stream().sorted(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center)).thenComparingLong(BlockPos::asLong)).toList();
    }

    static List<Site> resolve(ServerLevel level, List<BlockPos> positions, ServerPlayer player) {
        var sites = new ArrayList<Site>(positions.size());
        for (BlockPos pos : positions) {
            Site site = at(level, pos, player);
            if (site != null && site.pos().equals(pos)) {
                sites.add(site);
            }
        }
        return sites;
    }
}


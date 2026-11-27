package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

final class WeaselChests {
    static final int RANGE = 16;
    private static BiFunction<ServerLevel, Site, Label> labelReader = (level, site) -> null;
    private static Consumer<SnowcacheWeasel> workedBox = mob -> {};

    static void installPhysicalLabels(BiFunction<ServerLevel, Site, Label> reader, Consumer<SnowcacheWeasel> onWorkedBox) {
        labelReader = reader;
        workedBox = onWorkedBox;
    }

    static void worked(SnowcacheWeasel mob, Site site) {
        if (!site.routed()) workedBox.accept(mob);
    }

    enum Mode {
        BOTH, DEPOSIT, TAKE;

        boolean deposit() { return this != TAKE; }
        boolean take() { return this != DEPOSIT; }
        Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    enum Kind { BOX, INLET, OUTLET, GRID }

    record Endpoint(Kind kind, int priority, Object group, Object token, @Nullable BlockPos buffer,
                    @Nullable Direction face, @Nullable UUID bag) {
        boolean routed() { return kind != Kind.BOX; }
    }

    record Label(@Nullable Item item, @Nullable ResourceLocation tab) {
        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) : WeaselTabs.of(stack).equals(tab);
        }
    }

    record Site(BlockPos pos, WeaselInventory items, Endpoint endpoint, @Nullable Label label) {
        Site(BlockPos pos, WeaselInventory items, Endpoint endpoint) {
            this(pos, items, endpoint, null);
        }

        boolean routed() { return endpoint.routed(); }

        Mode mode(SnowcacheWeasel mob) {
            return switch (endpoint.kind()) {
                case INLET -> Mode.DEPOSIT;
                case OUTLET -> Mode.TAKE;
                case GRID -> mob.storageMode(this, Mode.TAKE);
                case BOX -> mob.storageMode(this, Mode.BOTH);
            };
        }

        int priority(SnowcacheWeasel mob) {
            return endpoint.priority() == 100 ? 100 : mob.storagePriority(pos, endpoint.priority());
        }

        int first() {
            for (int slot = 0; slot < items.size(); slot++) {
                if (!items.peek(slot).isEmpty()) return slot;
            }
            return -1;
        }

        @Nullable ResourceLocation category() {
            if (label != null) return label.tab();
            int first = first();
            return first < 0 ? null : WeaselTabs.of(items.peek(first));
        }

        boolean matchesPrimary(ItemStack stack) {
            return matchesPrimary(stack, category());
        }

        boolean matchesPrimary(ItemStack stack, @Nullable ResourceLocation tab) {
            if (label != null) return label.matches(stack);
            return tab == null || tab.equals(WeaselTabs.of(stack));
        }

        int firstSortableSlot(Mode mode) {
            if (mode == Mode.TAKE || label != null) return 0;
            return first() + 1;
        }

        int room(ItemStack stack, boolean fallback) {
            if (routed()) return items.insert(stack, true);
            if (label != null) return label.matches(stack) ? items.insert(stack, true) : 0;
            int room = 0;
            int first = first();
            for (int slot = 0; slot < items.size() && room < stack.getCount(); slot++) {
                if (canUseSlot(slot, first, stack, fallback)) {
                    room += items.insert(slot, stack, stack.getCount() - room, true);
                }
            }
            return room;
        }

        int deposit(ItemStack stack, boolean fallback) {
            int before = stack.getCount();
            if (routed() || label != null) {
                if (label != null && !label.matches(stack)) return 0;
                int accepted = items.insert(stack, false);
                stack.shrink(accepted);
                return accepted;
            }
            int first = first();
            int last = items.size() - 1;
            while (last >= 0 && items.peek(last).isEmpty()) last--;
            if (!fallback) {
                for (int slot = Math.max(first, 0); slot <= last && !stack.isEmpty(); slot++) {
                    if (!items.peek(slot).isEmpty()) insert(slot, stack);
                }
                for (int slot = first + 1; slot < items.size() && !stack.isEmpty(); slot++) {
                    if (items.peek(slot).isEmpty()) insert(slot, stack);
                }
            } else {
                // Keep the classification stack at the front when using an overflow container.
                for (int slot = last + 1; slot < items.size() && !stack.isEmpty(); slot++) {
                    if (items.peek(slot).isEmpty()) insert(slot, stack);
                }
                for (int slot = last; slot > first && !stack.isEmpty(); slot--) {
                    if (items.peek(slot).isEmpty()) insert(slot, stack);
                }
                for (int slot = last; slot >= Math.max(first, 0) && !stack.isEmpty(); slot--) {
                    if (!items.peek(slot).isEmpty()) insert(slot, stack);
                }
                for (int slot = first - 1; slot >= 0 && !stack.isEmpty() && WeaselTabs.of(stack).equals(category()); slot--) {
                    if (items.peek(slot).isEmpty()) insert(slot, stack);
                }
            }
            return before - stack.getCount();
        }

        private void insert(int slot, ItemStack stack) {
            stack.shrink(items.insert(slot, stack, stack.getCount(), false));
        }

        private boolean canUseSlot(int slot, int first, ItemStack stack, boolean fallback) {
            ItemStack existing = items.peek(slot);
            if (!existing.isEmpty()) return ItemStack.isSameItemSameTags(existing, stack);
            return slot > first || fallback && first >= 0 && WeaselTabs.of(stack).equals(category());
        }

        boolean fitsAtEnd(List<ItemStack> cargo) {
            if (routed() || label != null) return false;
            if (items instanceof WeaselInventory.Vanilla vanilla) return fitsVanilla(cargo, vanilla.container());
            int first = first();
            var counts = new int[items.size()];
            var stacks = new ItemStack[items.size()];
            for (int slot = 0; slot < stacks.length; slot++) {
                stacks[slot] = items.peek(slot);
            }
            for (ItemStack stack : cargo) {
                int remaining = stack.getCount();
                for (int slot = 0; slot < stacks.length && remaining > 0; slot++) {
                    if ((!stacks[slot].isEmpty() && !ItemStack.isSameItemSameTags(stacks[slot], stack))
                            || !canUseSlot(slot, first, stack, true)) continue;
                    int available = items.insert(slot, stack, stack.getMaxStackSize(), true) - counts[slot];
                    int moved = Math.min(remaining, Math.max(0, available));
                    if (moved > 0) {
                        counts[slot] += moved;
                        stacks[slot] = stack;
                        remaining -= moved;
                    }
                }
                if (remaining > 0) return false;
            }
            return true;
        }

        private boolean fitsVanilla(List<ItemStack> cargo, Container vanilla) {
            int first = first();
            ResourceLocation tab = category();
            var counts = new int[vanilla.getContainerSize()];
            var stacks = new ItemStack[counts.length];
            int last = -1;
            for (int i = 0; i < counts.length; i++) {
                stacks[i] = vanilla.getItem(i);
                counts[i] = stacks[i].getCount();
                if (counts[i] > 0) last = i;
            }
            for (ItemStack stack : cargo) {
                int remaining = stack.getCount();
                int limit = Math.min(stack.getMaxStackSize(), vanilla.getMaxStackSize());
                int tail = last;
                // Match the insertion order so accepting a full batch does not depend on a different slot layout.
                for (int i = tail + 1; i < counts.length && remaining > 0; i++) {
                    if (counts[i] == 0 && vanilla.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit);
                        stacks[i] = stack;
                        counts[i] = moved;
                        remaining -= moved;
                        last = i;
                    }
                }
                for (int i = tail; i > first && remaining > 0; i--) {
                    if (counts[i] == 0 && vanilla.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit);
                        stacks[i] = stack;
                        counts[i] = moved;
                        remaining -= moved;
                    }
                }
                for (int i = tail; i >= Math.max(first, 0) && remaining > 0; i--) {
                    if (counts[i] < limit && ItemStack.isSameItemSameTags(stacks[i], stack) && vanilla.canPlaceItem(i, stack)) {
                        int moved = Math.min(remaining, limit - counts[i]);
                        counts[i] += moved;
                        remaining -= moved;
                    }
                }
                for (int i = first - 1; i >= 0 && remaining > 0; i--) {
                    if (counts[i] == 0 && WeaselTabs.of(stack).equals(tab) && vanilla.canPlaceItem(i, stack)) {
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
        Site site = findSite(level, pos, owner);
        if (site == null || site.routed()) return site;
        Label label = labelReader.apply(level, site);
        return label == null ? site : new Site(site.pos(), site.items(), site.endpoint(), label);
    }

    private static @Nullable Site findSite(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (!level.hasChunkAt(pos) || !level.mayInteract(owner, pos)) {
            return null;
        }
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity block)
                || ((yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselLootChest) block).yagensMobs$lootTable() != null || !unlocked(block, owner)) {
            return WeaselPorts.at(level, pos, owner);
        }
        BlockPos canonical = pos.immutable();
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos paired = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!level.hasChunkAt(paired) || !level.mayInteract(owner, paired)
                    || !(level.getBlockEntity(paired) instanceof ChestBlockEntity other)
                    || ((yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselLootChest) other).yagensMobs$lootTable() != null || !unlocked(other, owner)) {
                return null;
            }
            if (paired.asLong() < pos.asLong()) {
                canonical = paired;
            }
        }
        Container inventory = ChestBlock.getContainer(chest, state, level, pos, false);
        return inventory == null ? null : new Site(canonical, new WeaselInventory.Vanilla(inventory),
                new Endpoint(Kind.BOX, 100, canonical, level.getBlockEntity(canonical), null, null, null));
    }

    private static boolean unlocked(ChestBlockEntity chest, ServerPlayer owner) {
        return ((yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselChestLock) chest).yagensMobs$lock().unlocksWith(owner.getMainHandItem());
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
                    if (pos.distSqr(center) <= RANGE * RANGE && (entry.getValue() instanceof ChestBlockEntity || WeaselPorts.handles(entry.getValue()))) {
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
        if (!WeaselPorts.enabled()) return sites;
        var buffers = new java.util.HashSet<BlockPos>();
        for (var site : sites) {
            if (site.endpoint().buffer() != null) buffers.add(site.endpoint().buffer());
        }
        sites.removeIf(site -> !site.routed() && (buffers.contains(site.pos())
                || WeaselPorts.externalGroup(level, site.pos()) != null
                || level.getBlockState(site.pos()).getBlock() instanceof ChestBlock
                && level.getBlockState(site.pos()).getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && WeaselPorts.externalGroup(level, site.pos().relative(ChestBlock.getConnectedDirection(level.getBlockState(site.pos())))) != null));
        return sites;
    }
}


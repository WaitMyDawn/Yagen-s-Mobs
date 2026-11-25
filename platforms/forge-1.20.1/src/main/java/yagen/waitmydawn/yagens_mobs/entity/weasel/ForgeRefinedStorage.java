package yagen.waitmydawn.yagens_mobs.entity.weasel;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.grid.GridType;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.blockentity.ExporterBlockEntity;
import com.refinedmods.refinedstorage.blockentity.ExternalStorageBlockEntity;
import com.refinedmods.refinedstorage.blockentity.ImporterBlockEntity;
import com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity;
import com.refinedmods.refinedstorage.blockentity.config.IType;
import com.refinedmods.refinedstorage.blockentity.config.IWhitelistBlacklist;
import com.refinedmods.refinedstorage.blockentity.grid.GridBlockEntity;
import com.refinedmods.refinedstorage.container.ExporterContainerMenu;
import com.refinedmods.refinedstorage.container.GridContainerMenu;
import com.refinedmods.refinedstorage.container.ImporterContainerMenu;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselChestLock;
import yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselLootChest;

final class ForgeRefinedStorage {
    static final int PRIORITY = 98;

    private ForgeRefinedStorage() {}

    static boolean supports(BlockEntity entity) { return kind(entity) != null; }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        if (entity instanceof GridBlockEntity grid) {
            var type = grid.getNode().getGridType();
            if (type == GridType.NORMAL || type == GridType.CRAFTING) return WeaselChests.Kind.GRID;
        }
        if (entity instanceof ImporterBlockEntity) return WeaselChests.Kind.INLET;
        if (entity instanceof ExporterBlockEntity) return WeaselChests.Kind.OUTLET;
        return null;
    }

    static boolean matchesMenu(WeaselChests.Kind kind, AbstractContainerMenu menu) {
        return switch (kind) {
            case GRID -> menu instanceof GridContainerMenu grid
                    && (grid.getGrid().getGridType() == GridType.NORMAL || grid.getGrid().getGridType() == GridType.CRAFTING);
            case INLET -> menu instanceof ImporterContainerMenu;
            case OUTLET -> menu instanceof ExporterContainerMenu;
            case BOX -> false;
        };
    }

    private static boolean allowed(INetwork network, ServerPlayer owner, Permission permission) {
        return network != null && network.getSecurityManager().hasPermission(permission, owner);
    }

    static boolean canConfigure(BlockEntity entity, ServerPlayer owner) {
        var node = ((NetworkNodeBlockEntity<?>) entity).getNode();
        var network = node.getNetwork();
        return switch (kind(entity)) {
            case GRID -> allowed(network, owner, Permission.EXTRACT) || allowed(network, owner, Permission.INSERT);
            case INLET, OUTLET -> allowed(network, owner, Permission.MODIFY);
            default -> false;
        };
    }

    static @Nullable Object externalGroup(ServerLevel level, BlockPos storage) {
        for (Direction direction : Direction.values()) {
            var pos = storage.relative(direction);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ExternalStorageBlockEntity external
                    && external.getNode().getDirection() == direction.getOpposite()) return external.getNode().getNetwork();
        }
        return null;
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var pos = entity.getBlockPos();
        var kind = kind(entity);
        var node = ((NetworkNodeBlockEntity<?>) entity).getNode();
        var network = node.getNetwork();
        boolean extract = allowed(network, owner, Permission.EXTRACT);
        boolean insert = allowed(network, owner, Permission.INSERT);
        if (entity instanceof GridBlockEntity grid) {
            var items = network != null && grid.getNode().isGridActive()
                    ? new GridInventory(network, extract, insert) : WeaselInventory.UNAVAILABLE;
            return new WeaselChests.Site(pos, items, new WeaselChests.Endpoint(kind, 100,
                    network == null ? entity : network, entity, null, null, null));
        }
        Direction side = node.getDirection();
        BlockPos attached = pos.relative(side);
        if (!level.hasChunkAt(attached) || !level.mayInteract(owner, attached)) return null;
        var block = level.getBlockEntity(attached);
        BlockPos buffer = attached;
        Object bufferGroup = attached;
        if (block instanceof BarrelBlockEntity barrel) {
            if (((WeaselLootChest) barrel).yagensMobs$lootTable() != null
                    || !((WeaselChestLock) barrel).yagensMobs$lock().unlocksWith(owner.getMainHandItem())) return null;
        } else if (block instanceof ChestBlockEntity || WeaselPorts.physical(block)) {
            var site = WeaselChests.at(level, attached, owner);
            if (site == null) return null;
            buffer = site.pos();
            bufferGroup = site.endpoint().group();
        } else {
            return null;
        }
        var handler = WeaselPorts.handler(level, attached, side.getOpposite());
        if (handler == null) return null;
        boolean inlet = kind == WeaselChests.Kind.INLET;
        WeaselInventory inventory = WeaselInventory.UNAVAILABLE;
        if (network != null && node.isActive() && node.getRedstoneMode().isEnabled(level.hasNeighborSignal(pos))
                && (inlet ? insert : extract) && externalGroup(level, attached) == null) {
            Predicate<ItemStack> filter = stack -> false;
            if (entity instanceof ImporterBlockEntity input && input.getNode().getType() == IType.ITEMS) {
                var port = input.getNode();
                filter = stack -> !stack.isEmpty() && IWhitelistBlacklist.acceptsItem(port.getItemFilters(),
                        port.getWhitelistBlacklistMode(), port.getCompare(), stack);
            } else if (entity instanceof ExporterBlockEntity output && output.getNode().getType() == IType.ITEMS) {
                var port = output.getNode();
                var entries = port.getItemFilters();
                filter = stack -> {
                    if (stack.isEmpty()) return false;
                    for (int slot = 0; slot < entries.getSlots(); slot++) {
                        var key = entries.getStackInSlot(slot);
                        if (!key.isEmpty() && com.refinedmods.refinedstorage.apiimpl.API.instance().getComparer()
                                .isEqual(key, stack, port.getCompare())) return true;
                    }
                    return false;
                };
            }
            var slots = new WeaselPorts.Slots(handler, !inlet, inlet, filter);
            inventory = inlet ? slots : new SupplyInventory(slots);
        }
        return new WeaselChests.Site(pos, inventory, new WeaselChests.Endpoint(kind, PRIORITY,
                network == null ? bufferGroup : network, new PortIdentity(entity, block, side), buffer, null, null));
    }

    private record PortIdentity(BlockEntity port, BlockEntity buffer, Direction side) {}

    private record SupplyInventory(WeaselPorts.Slots slots) implements WeaselInventory {
        @Override public boolean awaitsSupply() { return true; }
        @Override public int size() { return slots.size(); }
        @Override public ItemStack peek(int slot) { return slots.peek(slot); }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return slots.take(slot, amount, simulate); }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }
    }

    private record GridInventory(INetwork network, boolean extract, boolean insert) implements WeaselInventory {
        @Override public int size() { return 0; }
        @Override public ItemStack peek(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }

        @Override
        public int insert(ItemStack stack, boolean simulate) {
            return !insert ? 0 : stack.getCount() - network.insertItem(stack.copy(), stack.getCount(),
                    simulate ? Action.SIMULATE : Action.PERFORM).getCount();
        }

        @Override
        public void addStock(WeaselStock stock, Set<Item> relevant) {
            if (!extract) return;
            var list = network.getItemStorageCache().getList();
            for (Item item : relevant) {
                for (var entry : list.getStacks(new ItemStack(item))) stock.add(entry.getStack());
            }
        }

        @Override
        public boolean hasMaterial(WeaselStock pulls) {
            return extract && pulls.keys().stream().anyMatch(key -> !network.extractItem(key, 1, Action.SIMULATE).isEmpty());
        }

        @Override
        public boolean collect(WeaselStock pulls, WeaselStock cargo) {
            if (!extract) return false;
            boolean changed = false;
            for (ItemStack key : pulls.keys()) {
                var taken = network.extractItem(key, (int) Math.min(Integer.MAX_VALUE, pulls.count(key)), Action.PERFORM);
                if (!taken.isEmpty()) {
                    pulls.remove(taken, taken.getCount());
                    cargo.add(taken);
                    changed = true;
                }
            }
            return changed;
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.weasel;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.api.storage.root.FuzzyRootStorage;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.importer.ImporterNetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.filter.FilterMode;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.api.security.PlatformSecurityNetworkComponent;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity;
import com.refinedmods.refinedstorage.common.exporter.ExporterContainerMenu;
import com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity;
import com.refinedmods.refinedstorage.common.grid.CraftingGridBlockEntity;
import com.refinedmods.refinedstorage.common.grid.CraftingGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridBlockEntity;
import com.refinedmods.refinedstorage.common.grid.GridContainerMenu;
import com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity;
import com.refinedmods.refinedstorage.common.importer.ImporterContainerMenu;
import com.refinedmods.refinedstorage.common.security.BuiltinPermission;
import com.refinedmods.refinedstorage.common.storage.externalstorage.AbstractExternalStorageBlockEntity;
import com.refinedmods.refinedstorage.common.support.AbstractDirectionalBlock;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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

final class NeoRefinedStorage {
    static final int PRIORITY = 98;

    private NeoRefinedStorage() {}

    static boolean supports(BlockEntity entity) { return kind(entity) != null; }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        if (entity instanceof GridBlockEntity || entity instanceof CraftingGridBlockEntity) return WeaselChests.Kind.GRID;
        if (entity instanceof AbstractImporterBlockEntity) return WeaselChests.Kind.INLET;
        if (entity instanceof AbstractExporterBlockEntity) return WeaselChests.Kind.OUTLET;
        return null;
    }

    static boolean matchesMenu(WeaselChests.Kind kind, AbstractContainerMenu menu) {
        return switch (kind) {
            case GRID -> menu instanceof GridContainerMenu || menu instanceof CraftingGridContainerMenu;
            case INLET -> menu instanceof ImporterContainerMenu;
            case OUTLET -> menu instanceof ExporterContainerMenu;
            case BOX -> false;
        };
    }

    private static boolean allowed(Network network, ServerPlayer owner, BuiltinPermission permission) {
        return network != null && network.getComponent(PlatformSecurityNetworkComponent.class).isAllowed(permission, owner);
    }

    static boolean canConfigure(BlockEntity entity, ServerPlayer owner) {
        var node = (AbstractBaseNetworkNodeContainerBlockEntity<?>) entity;
        var network = node.getNetworkForItem();
        return network == null ? node.getContainerProvider().canBuild(owner) : allowed(network, owner, BuiltinPermission.OPEN);
    }

    static @Nullable Object group(BlockEntity entity) {
        return ((AbstractBaseNetworkNodeContainerBlockEntity<?>) entity).getNetworkForItem();
    }

    static @Nullable Object externalGroup(ServerLevel level, BlockPos storage) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = storage.relative(direction);
            if (level.hasChunkAt(neighbor)
                    && level.getBlockEntity(neighbor) instanceof AbstractExternalStorageBlockEntity external
                    && AbstractDirectionalBlock.tryExtractDirection(external.getBlockState()) == direction.getOpposite()
                    && external.getNetworkForItem() != null) {
                return external.getNetworkForItem();
            }
        }
        return null;
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var pos = entity.getBlockPos();
        var kind = kind(entity);
        var port = (AbstractBaseNetworkNodeContainerBlockEntity<?>) entity;
        var network = port.getNetworkForItem();
        boolean open = allowed(network, owner, BuiltinPermission.OPEN);
        boolean extract = open && allowed(network, owner, BuiltinPermission.EXTRACT);
        boolean insert = open && allowed(network, owner, BuiltinPermission.INSERT);
        if (entity instanceof AbstractGridBlockEntity grid) {
            var inventory = open && grid.isGridActive() && network != null
                    ? new GridInventory(grid.getItemStorage(), network.getComponent(StorageNetworkComponent.class),
                            new PlayerActor(owner), extract, insert)
                    : WeaselInventory.UNAVAILABLE;
            return new WeaselChests.Site(pos, inventory, new WeaselChests.Endpoint(kind, 100,
                    network == null ? entity : network, entity, null, null, null));
        }
        Direction side = AbstractDirectionalBlock.tryExtractDirection(entity.getBlockState());
        if (side == null) return null;
        BlockPos attached = pos.relative(side);
        if (!level.hasChunkAt(attached) || !level.mayInteract(owner, attached)) return null;

        // A port has no inventory of its own. Interact with its buffer using the same face as the RS node.
        var block = level.getBlockEntity(attached);
        BlockPos buffer = attached;
        Object bufferGroup = attached;
        if (block instanceof BarrelBlockEntity barrel) {
            if (barrel.getLootTable() != null || !((WeaselChestLock) barrel).yagensMobs$lock().unlocksWith(owner.getMainHandItem())) {
                return null;
            }
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
        var node = port.getContainerProvider().getContainers().stream().map(container -> container.getNode())
                .filter(AbstractNetworkNode.class::isInstance).map(AbstractNetworkNode.class::cast).findFirst().orElse(null);
        boolean active = node != null && node.isActive() && port.getRedstoneMode().isActive(level.hasNeighborSignal(pos));
        boolean inlet = kind == WeaselChests.Kind.INLET;
        WeaselInventory inventory = WeaselInventory.UNAVAILABLE;
        if (active && (inlet ? insert : extract) && externalGroup(level, attached) == null) {
            ResourceContainerData filters = entity instanceof AbstractImporterBlockEntity importer ? importer.getMenuData()
                    : ((AbstractExporterBlockEntity) entity).getMenuData().resourceContainerData();
            var filterConfig = new CompoundTag();
            port.writeConfiguration(filterConfig, level.registryAccess());
            boolean fuzzy = filterConfig.getBoolean("fm");
            boolean allowList = !(node instanceof ImporterNetworkNode importerNode) || importerNode.getFilterMode() == FilterMode.ALLOW;
            Set<ResourceKey> filter = new HashSet<>();
            filters.resources().stream().flatMap(Optional::stream).forEach(entry -> {
                if (entry.resource() instanceof ItemResource item) filter.add(fuzzy ? item.normalize() : item);
            });
            if (!allowList || !filter.isEmpty()) {
                var slots = new WeaselPorts.Slots(handler, !inlet, inlet, stack -> {
                    if (stack.isEmpty()) return false;
                    var item = ItemResource.ofItemStack(stack);
                    return filter.contains(fuzzy ? item.normalize() : item) == allowList;
                });
                inventory = inlet ? slots : new SupplyInventory(slots);
            }
        }
        return new WeaselChests.Site(pos, inventory,
                new WeaselChests.Endpoint(kind, PRIORITY, network == null ? bufferGroup : network,
                        new PortIdentity(entity, block, side), buffer, null, null));
    }

    private record PortIdentity(BlockEntity port, BlockEntity buffer, Direction side) {}

    private record SupplyInventory(WeaselPorts.Slots slots) implements WeaselInventory {
        @Override public boolean awaitsSupply() { return true; }
        @Override public int size() { return slots.size(); }
        @Override public ItemStack peek(int slot) { return slots.peek(slot); }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return slots.take(slot, amount, simulate); }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }
    }

    private record GridInventory(Storage storage, StorageNetworkComponent root, PlayerActor actor,
                                 boolean extract, boolean insert) implements WeaselInventory {
        @Override public int size() { return 0; }
        @Override public ItemStack peek(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }

        @Override
        public int insert(ItemStack stack, boolean simulate) {
            return !insert ? 0 : (int) storage.insert(ItemResource.ofItemStack(stack), stack.getCount(),
                    simulate ? Action.SIMULATE : Action.EXECUTE, actor);
        }

        @Override
        public void addStock(WeaselStock stock, Set<Item> relevant) {
            if (!extract) return;
            if (!(root instanceof FuzzyRootStorage fuzzy)) {
                for (var amount : storage.getAll()) {
                    if (amount.resource() instanceof ItemResource resource && relevant.contains(resource.toItemStack().getItem())) {
                        stock.add(resource.toItemStack(), amount.amount());
                    }
                }
                return;
            }
            for (Item item : relevant) {
                for (var key : fuzzy.getFuzzy(new ItemResource(item))) {
                    if (key instanceof ItemResource resource) stock.add(resource.toItemStack(), root.get(key));
                }
            }
        }

        @Override
        public boolean hasMaterial(WeaselStock pulls) {
            return extract && pulls.keys().stream().anyMatch(stack ->
                    storage.extract(ItemResource.ofItemStack(stack), 1L, Action.SIMULATE, actor) > 0);
        }

        @Override
        public boolean collect(WeaselStock pulls, WeaselStock cargo) {
            if (!extract) return false;
            boolean changed = false;
            for (var stack : pulls.keys()) {
                long taken = storage.extract(ItemResource.ofItemStack(stack), pulls.count(stack), Action.EXECUTE, actor);
                if (taken > 0) {
                    pulls.remove(stack, taken);
                    cargo.add(stack, taken);
                    changed = true;
                }
            }
            return changed;
        }
    }
}

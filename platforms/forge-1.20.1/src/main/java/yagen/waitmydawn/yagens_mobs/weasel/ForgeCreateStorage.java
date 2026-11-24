package yagen.waitmydawn.yagens_mobs.weasel;

import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class ForgeCreateStorage {
    static final int PRIORITY = 99;
    private static final Map<BlockEntity, DroppedStack> DROPPED = new WeakHashMap<>();

    private record DroppedStack(WeakReference<ItemEntity> entity, ItemStack original) {}

    public static void rememberOutput(BlockEntity funnel, Entity entity) {
        if (WeaselPorts.CREATE && entity instanceof ItemEntity drop && funnel.getLevel() instanceof ServerLevel level
                && attachedToVault(level, funnel)) {
            DROPPED.put(funnel, new DroppedStack(new WeakReference<>(drop), drop.getItem().copy()));
        }
    }

    private static @Nullable ItemEntity output(FunnelBlockEntity funnel, ServerPlayer owner) {
        var remembered = DROPPED.get(funnel);
        if (remembered == null) return null;
        var drop = remembered.entity().get();
        // Track the actual emitted entity. A pile that acquired another stack through merging is no longer a safe handoff.
        if (drop == null || !drop.isAlive() || drop.getItem().getCount() > remembered.original().getCount()
                || !ItemStack.isSameItemSameTags(drop.getItem(), remembered.original())
                || drop.distanceToSqr(Vec3.atCenterOf(funnel.getBlockPos())) > 6.25) {
            DROPPED.remove(funnel);
            return null;
        }
        return owner.serverLevel().mayInteract(owner, drop.blockPosition()) ? drop : null;
    }

    private ForgeCreateStorage() {}

    static boolean supports(BlockEntity entity) {
        return entity instanceof FunnelBlockEntity && entity.getBlockState().getBlock() instanceof AbstractFunnelBlock;
    }

    static boolean attachedToVault(Level level, BlockEntity entity) {
        Direction face = AbstractFunnelBlock.getFunnelFacing(entity.getBlockState());
        BlockPos attached = entity.getBlockPos().relative(face.getOpposite());
        return level.hasChunkAt(attached) && level.getBlockEntity(attached) instanceof ItemVaultBlockEntity;
    }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        var state = entity.getBlockState();
        boolean outlet;
        if (state.getBlock() instanceof FunnelBlock) {
            outlet = state.getValue(FunnelBlock.EXTRACTING);
        } else if (state.getBlock() instanceof BeltFunnelBlock) {
            var shape = state.getValue(BeltFunnelBlock.SHAPE);
            if (shape == BeltFunnelBlock.Shape.PULLING) return WeaselChests.Kind.INLET;
            if (shape == BeltFunnelBlock.Shape.PUSHING) return WeaselChests.Kind.OUTLET;
            Level level = entity.getLevel();
            BlockPos below = entity.getBlockPos().below();
            if (level == null || !level.hasChunkAt(below)) return null;
            var belt = BeltHelper.getSegmentBE(level, below);
            if (belt == null) return null;
            outlet = belt.getMovementFacing() == state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } else {
            return null;
        }
        return outlet ? WeaselChests.Kind.OUTLET : WeaselChests.Kind.INLET;
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var funnel = (FunnelBlockEntity) entity;
        var kind = kind(funnel);
        if (kind == null) return null;
        BlockPos pos = funnel.getBlockPos();
        Direction face = AbstractFunnelBlock.getFunnelFacing(funnel.getBlockState());
        BlockPos attached = pos.relative(face.getOpposite());
        if (!level.hasChunkAt(attached) || !(level.getBlockEntity(attached) instanceof ItemVaultBlockEntity vault)) return null;
        BlockPos root = vault.getController();
        if (root == null || !level.hasChunkAt(root) || !(level.getBlockEntity(root) instanceof ItemVaultBlockEntity controller)) {
            return null;
        }
        Direction.Axis axis = ItemVaultBlock.getVaultBlockAxis(controller.getBlockState());
        int width = controller.getWidth();
        int length = controller.getHeight();
        // A vault's combined capability can address every member, including members across a chunk or protection boundary.
        for (int along = 0; along < length; along++) {
            for (int y = 0; y < width; y++) {
                for (int across = 0; across < width; across++) {
                    BlockPos member = root.offset(axis == Direction.Axis.X ? along : across, y, axis == Direction.Axis.X ? across : along);
                    if (!level.hasChunkAt(member) || !level.mayInteract(owner, member)
                            || !(level.getBlockEntity(member) instanceof ItemVaultBlockEntity part) || !root.equals(part.getController())
                            || WeaselPorts.externalGroup(level, member) != null) return null;
                }
            }
        }
        boolean inlet = kind == WeaselChests.Kind.INLET;
        var manipulate = funnel.getBehaviour(InvManipulationBehaviour.TYPE);
        var filter = funnel.getBehaviour(FilteringBehaviour.TYPE);
        var handler = WeaselPorts.handler(level, attached, face);
        WeaselInventory items = WeaselInventory.UNAVAILABLE;
        DepotBlockEntity handoff = null;
        if (handler != null && !funnel.getBlockState().getValue(AbstractFunnelBlock.POWERED)) {
            // A depot below a belt funnel receives real output and may hold the last stack after it leaves the vault.
            BlockPos below = pos.below();
            if (!inlet && funnel.getBlockState().getBlock() instanceof BeltFunnelBlock && level.hasChunkAt(below)
                    && level.getBlockEntity(below) instanceof DepotBlockEntity depot && level.mayInteract(owner, below)) {
                var staged = WeaselPorts.handler(level, below, Direction.UP);
                if (staged != null) {
                    handoff = depot;
                    items = new DepotOutput(new WeaselPorts.Slots(staged, true, false, stack -> matches(filter, stack)));
                }
            }
            if (handoff == null && manipulate != null && manipulate.getInventory() != null) {
                items = new FunnelInventory(funnel, manipulate, manipulate.getInventory(), filter, inlet);
            }
            if (!inlet && funnel.getBlockState().getBlock() instanceof FunnelBlock) {
                items = new GroundOutput(funnel, owner, filter, items);
            }
        }
        return new WeaselChests.Site(pos, items, new WeaselChests.Endpoint(kind, PRIORITY, root,
                new VaultPort(funnel, controller, handoff, face, width, length), handoff == null ? null : handoff.getBlockPos(), face, null));
    }

    private static boolean matches(@Nullable FilteringBehaviour filter, ItemStack stack) {
        return !stack.isEmpty() && (filter == null || !filter.isActive() || filter.test(stack));
    }

    private record VaultPort(FunnelBlockEntity funnel, ItemVaultBlockEntity controller, @Nullable DepotBlockEntity depot,
                             Direction face, int width, int length) {}

    private record DepotOutput(WeaselPorts.Slots slots) implements WeaselInventory {
        @Override public boolean awaitsSupply() { return true; }
        @Override public int size() { return slots.size(); }
        @Override public ItemStack peek(int slot) { return slots.peek(slot); }
        @Override public ItemStack take(int slot, int amount, boolean simulate) { return slots.take(slot, amount, simulate); }
        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }
    }

    private record GroundOutput(FunnelBlockEntity funnel, ServerPlayer owner, @Nullable FilteringBehaviour filter,
                                WeaselInventory direct) implements WeaselInventory {
        @Override public int size() { return direct.size() + 1; }

        @Override
        public ItemStack peek(int slot) {
            if (slot != 0) return direct.peek(slot - 1);
            var drop = output(funnel, owner);
            return drop != null && matches(filter, drop.getItem()) ? drop.getItem() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack take(int slot, int amount, boolean simulate) {
            if (slot != 0) return direct.take(slot - 1, amount, simulate);
            var drop = output(funnel, owner);
            if (drop == null || !matches(filter, drop.getItem()) || amount <= 0) return ItemStack.EMPTY;
            var stack = drop.getItem();
            int moved = Math.min(stack.getCount(), amount);
            var taken = stack.copyWithCount(moved);
            if (!simulate) {
                if (moved == stack.getCount()) drop.discard();
                else drop.setItem(stack.copyWithCount(stack.getCount() - moved));
            }
            return taken;
        }

        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }

        @Override
        public void addStock(WeaselStock stock, Set<Item> relevant) {
            var stack = peek(0);
            if (!stack.isEmpty() && (relevant.isEmpty() || relevant.contains(stack.getItem()))) stock.add(stack);
            direct.addStock(stock, relevant);
        }
    }

    private record FunnelInventory(FunnelBlockEntity funnel, InvManipulationBehaviour manipulate,
                                   IItemHandler handler, @Nullable FilteringBehaviour filter, boolean inlet) implements WeaselInventory {
        @Override public int size() { return inlet ? 0 : handler.getSlots(); }

        @Override
        public ItemStack peek(int slot) {
            ItemStack stack = handler.getStackInSlot(slot);
            return matches(filter, stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack take(int slot, int amount, boolean simulate) {
            var stack = peek(slot);
            if (inlet || stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            var mode = funnel.getModeToExtract();
            int limit = funnel.getAmountToExtract();
            if (mode == ExtractionCountMode.EXACTLY && amount < limit) return ItemStack.EMPTY;
            if (simulate) manipulate.simulate();
            var extracted = manipulate.extract(mode, Math.min(amount, limit), item -> ItemStack.isSameItemSameTags(stack, item));
            if (!simulate && !extracted.isEmpty()) {
                funnel.flap(false);
                funnel.onTransfer(extracted);
            }
            return extracted;
        }

        @Override
        public void addStock(WeaselStock stock, Set<Item> relevant) {
            if (inlet) return;
            for (int slot = 0; slot < size(); slot++) {
                var stack = peek(slot);
                if (!stack.isEmpty() && (relevant.isEmpty() || relevant.contains(stack.getItem()))) {
                    int available = handler.extractItem(slot, Math.min(stack.getCount(), stack.getMaxStackSize()), true).getCount();
                    if (available > 0) stock.add(stack, available);
                }
            }
        }

        @Override public int insert(int slot, ItemStack stack, int amount, boolean simulate) { return 0; }

        @Override
        public int insert(ItemStack stack, boolean simulate) {
            if (!inlet || !matches(filter, stack)) return 0;
            if (simulate) manipulate.simulate();
            int accepted = stack.getCount() - manipulate.insert(stack.copy()).getCount();
            if (!simulate && accepted > 0) {
                funnel.flap(true);
                funnel.onTransfer(stack.copyWithCount(accepted));
            }
            return accepted;
        }
    }
}

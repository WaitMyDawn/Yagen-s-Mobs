package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

record FabricSlotInventory(SlottedStorage<ItemVariant> storage, boolean read, boolean write,
                           Predicate<ItemStack> filter, boolean awaitingSupply) implements WeaselInventory {
    static @Nullable SlottedStorage<ItemVariant> at(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        if (!level.hasChunkAt(pos)) return null;
        var inventory = ItemStorage.SIDED.find(level, pos, side);
        return inventory instanceof SlottedStorage<ItemVariant> slots ? slots : null;
    }

    @Override public int size() { return storage.getSlotCount(); }
    @Override public boolean awaitsSupply() { return awaitingSupply; }

    @Override
    public ItemStack peek(int index) {
        var slot = storage.getSlot(index);
        if (slot.isResourceBlank()) return ItemStack.EMPTY;
        var key = slot.getResource();
        var stack = key.toStack((int) Math.min(slot.getAmount(), key.toStack().getMaxStackSize()));
        return filter.test(stack) ? stack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack take(int index, int amount, boolean simulate) {
        var stack = peek(index);
        if (!read || amount <= 0 || stack.isEmpty()) return ItemStack.EMPTY;
        try (var transfer = Transaction.openOuter()) {
            long moved = storage.getSlot(index).extract(ItemVariant.of(stack), Math.min(amount, stack.getCount()), transfer);
            if (!simulate && moved > 0) transfer.commit();
            return stack.copyWithCount((int) moved);
        }
    }

    @Override
    public int insert(int index, ItemStack stack, int amount, boolean simulate) {
        if (!write || amount <= 0 || !filter.test(stack)) return 0;
        try (var transfer = Transaction.openOuter()) {
            long moved = storage.getSlot(index).insert(ItemVariant.of(stack), amount, transfer);
            if (!simulate && moved > 0) transfer.commit();
            return (int) moved;
        }
    }

    @Override
    public int insert(ItemStack stack, boolean simulate) {
        if (!write || stack.isEmpty() || !filter.test(stack)) return 0;
        try (var transfer = Transaction.openOuter()) {
            long moved = storage.insert(ItemVariant.of(stack), stack.getCount(), transfer);
            if (!simulate && moved > 0) transfer.commit();
            return (int) moved;
        }
    }
}

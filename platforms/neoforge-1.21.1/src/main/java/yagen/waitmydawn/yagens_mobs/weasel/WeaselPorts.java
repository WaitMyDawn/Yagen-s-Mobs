package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

final class WeaselPorts {
    static final boolean REFINED = installed("refinedstorage", "2.0.9");
    static final boolean CREATE = installed("create", "6.0.10");
    private static final boolean IRON = installed("ironchest", "1.21-neoforge-16.0.7");
    private static final boolean BACKPACKS = installed("sophisticatedbackpacks", "3.26.23")
            && installed("sophisticatedcore", "1.5.41");

    private WeaselPorts() {}

    private static boolean installed(String id, String version) {
        return ModList.get().getModContainerById(id).map(mod ->
                mod.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(version)) >= 0).orElse(false);
    }

    static boolean enabled() { return REFINED || CREATE; }

    static boolean handles(BlockEntity entity) {
        return (IRON || BACKPACKS) && NeoPhysicalStorage.supports(entity)
                || REFINED && NeoRefinedStorage.supports(entity)
                || CREATE && NeoCreateStorage.supports(entity);
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        if ((IRON || BACKPACKS) && NeoPhysicalStorage.supports(entity)) return NeoPhysicalStorage.at(level, entity, owner);
        if (REFINED && NeoRefinedStorage.supports(entity)) return NeoRefinedStorage.at(level, entity, owner);
        if (CREATE && NeoCreateStorage.supports(entity)) return NeoCreateStorage.at(level, entity, owner);
        return null;
    }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        if (REFINED && NeoRefinedStorage.supports(entity)) return NeoRefinedStorage.kind(entity);
        if (CREATE && NeoCreateStorage.supports(entity)) return NeoCreateStorage.kind(entity);
        return null;
    }

    static boolean isRefined(BlockEntity entity) { return REFINED && NeoRefinedStorage.supports(entity); }
    static boolean isCreate(BlockEntity entity) { return CREATE && NeoCreateStorage.supports(entity); }
    static boolean isVaultPort(Level level, BlockEntity entity) {
        return isCreate(entity) && NeoCreateStorage.attachedToVault(level, entity);
    }

    static boolean physical(BlockEntity entity) {
        return (IRON || BACKPACKS) && NeoPhysicalStorage.supports(entity);
    }

    static @Nullable Object externalGroup(ServerLevel level, BlockPos pos) {
        return REFINED ? NeoRefinedStorage.externalGroup(level, pos) : null;
    }

    static void lid(ServerLevel level, BlockPos pos, ServerPlayer owner, boolean open) {
        if ((IRON || BACKPACKS) && level.hasChunkAt(pos)) NeoPhysicalStorage.lid(level.getBlockEntity(pos), owner, open);
    }

    static @Nullable IItemHandler handler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.hasChunkAt(pos) ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) : null;
    }

    record Slots(IItemHandler handler, boolean read, boolean write, Predicate<ItemStack> allowed) implements WeaselInventory {
        @Override public int size() { return handler.getSlots(); }

        @Override
        public ItemStack peek(int slot) {
            ItemStack stack = handler.getStackInSlot(slot);
            return allowed.test(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack take(int slot, int amount, boolean simulate) {
            if (!read || amount <= 0 || peek(slot).isEmpty()) return ItemStack.EMPTY;
            return handler.extractItem(slot, Math.min(amount, peek(slot).getMaxStackSize()), simulate);
        }

        @Override
        public int insert(int slot, ItemStack stack, int amount, boolean simulate) {
            if (!write || amount <= 0 || !allowed.test(stack)) return 0;
            ItemStack remainder = handler.insertItem(slot, stack.copyWithCount(amount), simulate);
            return amount - remainder.getCount();
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

final class WeaselPorts {
    static final boolean REFINED = installed("refinedstorage", "1.12.4");
    static final boolean CREATE = installed("create", "6.0.8");
    private static final boolean IRON = installed("ironchest", "1.20.1-14.4.4");
    private static final boolean BACKPACKS = installed("sophisticatedbackpacks", "3.26.22")
            && installed("sophisticatedcore", "1.5.40");

    private WeaselPorts() {}

    private static boolean installed(String id, String version) {
        return ModList.get().getModContainerById(id).map(mod ->
                mod.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(version)) >= 0).orElse(false);
    }

    static boolean enabled() { return REFINED || CREATE; }

    static boolean handles(BlockEntity entity) {
        return (IRON || BACKPACKS) && ForgePhysicalStorage.supports(entity)
                || REFINED && ForgeRefinedStorage.supports(entity)
                || CREATE && ForgeCreateStorage.supports(entity);
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        if ((IRON || BACKPACKS) && ForgePhysicalStorage.supports(entity)) return ForgePhysicalStorage.at(level, entity, owner);
        if (REFINED && ForgeRefinedStorage.supports(entity)) return ForgeRefinedStorage.at(level, entity, owner);
        if (CREATE && ForgeCreateStorage.supports(entity)) return ForgeCreateStorage.at(level, entity, owner);
        return null;
    }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        if (REFINED && ForgeRefinedStorage.supports(entity)) return ForgeRefinedStorage.kind(entity);
        if (CREATE && ForgeCreateStorage.supports(entity)) return ForgeCreateStorage.kind(entity);
        return null;
    }

    static boolean isRefined(BlockEntity entity) { return REFINED && ForgeRefinedStorage.supports(entity); }
    static boolean isCreate(BlockEntity entity) { return CREATE && ForgeCreateStorage.supports(entity); }
    static boolean isVaultPort(Level level, BlockEntity entity) {
        return isCreate(entity) && ForgeCreateStorage.attachedToVault(level, entity);
    }

    static boolean physical(BlockEntity entity) {
        return (IRON || BACKPACKS) && ForgePhysicalStorage.supports(entity);
    }

    static @Nullable Object externalGroup(ServerLevel level, BlockPos pos) {
        return REFINED ? ForgeRefinedStorage.externalGroup(level, pos) : null;
    }

    static void lid(ServerLevel level, BlockPos pos, ServerPlayer owner, boolean open) {
        if ((IRON || BACKPACKS) && level.hasChunkAt(pos)) ForgePhysicalStorage.lid(level.getBlockEntity(pos), owner, open);
    }

    static @Nullable IItemHandler handler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        if (!level.hasChunkAt(pos)) return null;
        var block = level.getBlockEntity(pos);
        return block == null ? null : block.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
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

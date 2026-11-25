package yagen.waitmydawn.yagens_mobs.entity.weasel;

import anner.ironchest.blocks.blockentities.GenericChestEntity;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import org.jetbrains.annotations.Nullable;

final class FabricPhysicalStorage {
    private FabricPhysicalStorage() {}

    static boolean supports(BlockEntity entity) {
        return iron(entity) || backpack(entity);
    }

    private static boolean ironLoaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ironchest");
    }

    private static boolean backpackLoaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks");
    }

    static boolean iron(Object entity) { return ironLoaded() && entity instanceof GenericChestEntity; }
    private static boolean backpack(Object entity) { return backpackLoaded() && entity instanceof BackpackBlockEntity; }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var pos = entity.getBlockPos();
        if (iron(entity)) {
            var chest = (GenericChestEntity) entity;
            if (chest.getLootTable() != null || !((yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselChestLock) chest).yagensMobs$lock().unlocksWith(owner.getMainHandItem())
                    || level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) return null;
        }
        var inventory = FabricSlotInventory.at(level, pos, Direction.UP);
        if (inventory == null) return null;
        Object group = pos;
        UUID bag = null;
        if (backpack(entity)) {
            var backpack = (BackpackBlockEntity) entity;
            bag = backpack.getBackpackWrapper().getContentsUuid().orElse(null);
            if (bag != null) group = bag;
        }
        return new WeaselChests.Site(pos, new FabricSlotInventory(inventory, true, true, stack -> true, false),
                new WeaselChests.Endpoint(WeaselChests.Kind.BOX, 100, group, entity, null, null, bag));
    }

    static void lid(BlockEntity entity, ServerPlayer owner, boolean open) {
        if (!iron(entity)) return;
        var level = entity.getLevel();
        if (level != null) level.blockEvent(entity.getBlockPos(), entity.getBlockState().getBlock(), 1, open ? 1 : 0);
    }
}

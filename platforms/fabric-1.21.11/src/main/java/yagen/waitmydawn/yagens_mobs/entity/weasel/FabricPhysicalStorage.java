package yagen.waitmydawn.yagens_mobs.entity.weasel;

import anner.ironchest.blocks.blockentities.GenericChestEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

final class FabricPhysicalStorage {
    private FabricPhysicalStorage() {}

    static boolean supports(BlockEntity entity) {
        return iron(entity);
    }

    private static boolean ironLoaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ironchest");
    }

    static boolean iron(Object entity) { return ironLoaded() && entity instanceof GenericChestEntity; }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var pos = entity.getBlockPos();
        if (iron(entity)) {
            var chest = (GenericChestEntity) entity;
            if (chest.getLootTable() != null || !chest.canOpen(owner)
                    || level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) return null;
        }
        var inventory = FabricSlotInventory.at(level, pos, Direction.UP);
        if (inventory == null) return null;
        Object group = pos;
        return new WeaselChests.Site(pos, new FabricSlotInventory(inventory, true, true, stack -> true, false),
                new WeaselChests.Endpoint(WeaselChests.Kind.BOX, 100, group, entity, null, null, null));
    }

    static void lid(BlockEntity entity, ServerPlayer owner, boolean open) {
        if (!iron(entity)) return;
        var level = entity.getLevel();
        if (level != null) level.blockEvent(entity.getBlockPos(), entity.getBlockState().getBlock(), 1, open ? 1 : 0);
    }
}

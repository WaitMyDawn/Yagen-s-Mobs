package yagen.waitmydawn.yagens_mobs.weasel;

import com.progwml6.ironchest.common.block.regular.AbstractIronChestBlock;
import com.progwml6.ironchest.common.block.regular.entity.AbstractIronChestBlockEntity;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.weasel.mixin.WeaselChestLock;

final class ForgePhysicalStorage {
    private ForgePhysicalStorage() {}

    static boolean supports(BlockEntity entity) {
        return iron(entity) || backpack(entity);
    }

    private static boolean ironLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("ironchest");
    }

    private static boolean backpackLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedbackpacks");
    }

    static boolean iron(Object entity) { return ironLoaded() && entity instanceof AbstractIronChestBlockEntity; }
    private static boolean backpack(Object entity) { return backpackLoaded() && entity instanceof BackpackBlockEntity; }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockEntity entity, ServerPlayer owner) {
        var pos = entity.getBlockPos();
        if (iron(entity)) {
            var chest = (AbstractIronChestBlockEntity) entity;
            if (((yagen.waitmydawn.yagens_mobs.weasel.mixin.WeaselLootChest) chest).yagensMobs$lootTable() != null || !((WeaselChestLock) chest).yagensMobs$lock().unlocksWith(owner.getMainHandItem())
                    || AbstractIronChestBlock.isChestBlockedAt(level, pos)) return null;
        }
        var inventory = WeaselPorts.handler(level, pos, Direction.UP);
        if (inventory == null) return null;
        Object group = pos;
        UUID bag = null;
        if (backpack(entity)) {
            var backpack = (BackpackBlockEntity) entity;
            bag = backpack.getBackpackWrapper().getContentsUuid().orElse(null);
            var linked = backpack.getLinkedStorageEndpointData();
            if (linked != null) group = linked.groupId();
            else if (bag != null) group = bag;
        }
        return new WeaselChests.Site(pos, new WeaselPorts.Slots(inventory, true, true, stack -> true),
                new WeaselChests.Endpoint(WeaselChests.Kind.BOX, 100, group, entity, null, null, bag));
    }

    static void lid(BlockEntity entity, ServerPlayer owner, boolean open) {
        if (iron(entity)) {
            var chest = (AbstractIronChestBlockEntity) entity;
            var level = chest.getLevel();
            var pos = chest.getBlockPos();
            if (level != null && (open || AbstractIronChestBlockEntity.getOpenCount(level, pos) == 0)) {
                level.blockEvent(pos, chest.getBlockState().getBlock(), 1, open ? 1 : 0);
            }
        } else if (backpack(entity)) {
            var bag = (BackpackBlockEntity) entity;
            if (open) bag.startOpen(owner);
            else bag.stopOpen(owner);
        }
    }
}

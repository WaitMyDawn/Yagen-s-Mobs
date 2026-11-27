package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class MoltVoidProtection {
    private static final String COATING = "yagens_mobs:salvage_ray_molt";
    private static final String USED = "yagens_mobs:void_cloud_used";

    private MoltVoidProtection() {}

    public static void anvil(AnvilUpdateEvent event) {
        ItemStack equipment = event.getLeft();
        if (!event.getRight().is(ModItems.SALVAGE_RAY_MOLT.get()) || coated(equipment)
                || !(equipment.getItem() instanceof Equipable wearable) || !wearable.getEquipmentSlot().isArmor()) {
            return;
        }
        ItemStack result = equipment.copyWithCount(1);
        result.getOrCreateTag().putBoolean(COATING, true);
        if (event.getName() != null && !event.getName().isBlank() && !event.getName().equals(equipment.getHoverName().getString())) {
            result.setHoverName(Component.literal(event.getName()));
        }
        event.setOutput(result);
        event.setMaterialCost(1);
        event.setCost(1);
    }

    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var cloud = ModBlocks.VOID_CLOUD.get();
        var status = player.getPersistentData();
        int bottom = level.getMinBuildHeight();
        if (player.getY() > bottom + 4 || player.onGround() && !level.getBlockState(player.getOnPos()).is(cloud)) {
            status.remove(USED);
            return;
        }
        if (status.getBoolean(USED) || player.onGround() || player.isPassenger() || player.getAbilities().flying
                || player.getDeltaMovement().y >= 0 || player.getY() > bottom + 1.05) {
            return;
        }
        ItemStack charge = wornCoating(player);
        if (charge.isEmpty()) return;
        BlockPos center = new BlockPos(player.getBlockX(), bottom, player.getBlockZ());
        if (!level.hasChunkAt(center) || !level.getWorldBorder().isWithinBounds(center)
                || !level.getBlockState(center).isAir()
                || !level.setBlock(center, cloud.defaultBlockState(), Block.UPDATE_ALL)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            if (level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, cloud.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        charge.removeTagKey(COATING);
        // Keep additional charges for a later fall when this platform expires.
        status.putBoolean(USED, true);
        player.teleportTo(player.getX(), bottom + 1.0, player.getZ());
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    private static ItemStack wornCoating(ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack equipment = player.getItemBySlot(slot);
            if (coated(equipment)) return equipment;
        }
        return ItemStack.EMPTY;
    }

    public static boolean coated(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(COATING);
    }
}

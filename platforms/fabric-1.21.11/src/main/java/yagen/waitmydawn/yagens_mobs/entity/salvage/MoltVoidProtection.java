package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class MoltVoidProtection {
    private static final String COATING = "yagens_mobs:salvage_ray_molt";
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> USED =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("yagens_mobs", "void_cloud_used"),
                    builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

    private MoltVoidProtection() {}

    public static ItemStack coatingResult(ItemStack equipment, ItemStack material, String name) {
        if (!material.is(ModItems.SALVAGE_RAY_MOLT.get()) || coated(equipment)
                || equipment.get(DataComponents.EQUIPPABLE) == null
                || !equipment.get(DataComponents.EQUIPPABLE).slot().isArmor()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = equipment.copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(COATING, true));
        if (name != null && !name.isBlank() && !name.equals(equipment.getHoverName().getString())) {
            result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return result;
    }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        var cloud = ModBlocks.VOID_CLOUD.get();
        int bottom = level.getMinY();
        if (player.getY() > bottom + 4 || player.onGround() && !level.getBlockState(player.getOnPos()).is(cloud)) {
            player.removeAttached(USED);
            return;
        }
        if (Boolean.TRUE.equals(player.getAttached(USED)) || player.onGround() || player.isPassenger() || player.getAbilities().flying
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
        CustomData.update(DataComponents.CUSTOM_DATA, charge, tag -> tag.remove(COATING));
        // Keep additional charges for a later fall when this platform expires.
        player.setAttached(USED, true);
        player.teleportTo(player.getX(), bottom + 1.0, player.getZ());
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
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
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(COATING, false);
    }
}

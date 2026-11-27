package yagen.waitmydawn.yagens_mobs.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.yagens_mobs.entity.salvage.MoltVoidProtection;

@Mixin(ItemStack.class)
abstract class MoltEquipmentTooltip {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void yagens$showCoating(Item.TooltipContext context, Player viewer, TooltipFlag flag,
                                   CallbackInfoReturnable<List<Component>> callback) {
        var tooltip = callback.getReturnValue();
        if (!Minecraft.getInstance().hasShiftDown() || tooltip.isEmpty()
                || !MoltVoidProtection.coated((ItemStack) (Object) this)) return;
        var lines = new ArrayList<>(tooltip);
        lines.add(Component.translatable("item.yagens_mobs.salvage_ray_molt.coating")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        callback.setReturnValue(lines);
    }
}

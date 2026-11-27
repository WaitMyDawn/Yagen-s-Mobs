package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.salvage.MoltVoidProtection;

@Mixin(AnvilMenu.class)
abstract class MoltAnvil {
    @Shadow @Final private DataSlot cost;
    @Shadow private int repairItemCountCost;
    @Shadow private String itemName;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void yagens$coatEquipment(CallbackInfo callback) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack result = MoltVoidProtection.coatingResult(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), itemName);
        if (result.isEmpty()) return;
        cost.set(1);
        repairItemCountCost = 1;
        menu.getSlot(2).set(result);
        menu.broadcastChanges();
        callback.cancel();
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;

@Mixin(Gui.class)
abstract class VelocipodExperienceHud {
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void yagensMobs$hideBar(GuiGraphics graphics, int x, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof Velocipod) callback.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void yagensMobs$hideLevel(GuiGraphics graphics, DeltaTracker ticks, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof Velocipod) callback.cancel();
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

@Mixin(ExperienceBarRenderer.class)
abstract class VelocipodExperienceHud {
    @Inject(method = {"render", "renderBackground"}, at = @At("HEAD"), cancellable = true)
    private void yagensMobs$hideBar(GuiGraphics graphics, DeltaTracker ticks, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof Velocipod) callback.cancel();
    }
}

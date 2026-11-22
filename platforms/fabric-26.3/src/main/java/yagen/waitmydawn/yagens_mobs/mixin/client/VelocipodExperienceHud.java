package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;

@Mixin(ExperienceBar.class)
abstract class VelocipodExperienceHud {
    @Inject(method = {"extractRenderState", "extractBackground"}, at = @At("HEAD"), cancellable = true)
    private void yagensMobs$hideBar(GuiGraphicsExtractor graphics, DeltaTracker ticks, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof Velocipod) callback.cancel();
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;

@Mixin(WaterFogEnvironment.class)
abstract class BubbleSalamanderFog {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void yagensMobs$clearWaterFog(FogData data, Camera camera, ClientLevel level,
                                       float distance, DeltaTracker delta, CallbackInfo callback) {
        var game = Minecraft.getInstance();
        if (game.player != null && game.options.getCameraType().isFirstPerson() && camera.entity() == game.player
                && BubbleSalamander.isProvidingAir(game.player)) {
            data.environmentalEnd *= 1.7F;
        }
    }
}

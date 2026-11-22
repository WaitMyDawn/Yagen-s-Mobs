package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;

@Mixin(LightmapRenderStateExtractor.class)
abstract class BubbleSalamanderNightVision {
    @Inject(method = "extract", at = @At("RETURN"))
    private void yagensMobs$provideVision(LightmapRenderState state, float partialTick, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && BubbleSalamander.isProvidingAir(player)) {
            state.nightVisionEffectIntensity = 1;
        }
    }
}

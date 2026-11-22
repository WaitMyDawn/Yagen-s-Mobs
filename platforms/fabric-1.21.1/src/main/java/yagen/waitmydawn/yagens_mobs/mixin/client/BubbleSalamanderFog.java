package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;

@Mixin(FogRenderer.class)
abstract class BubbleSalamanderFog {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void yagensMobs$clearWaterFog(Camera camera, FogRenderer.FogMode mode, float far,
                                               boolean nearFog, float partialTick, CallbackInfo callback) {
        var game = Minecraft.getInstance();
        if (game.player != null && game.options.getCameraType().isFirstPerson() && camera.getEntity() == game.player
                && camera.getFluidInCamera() == FogType.WATER && BubbleSalamander.isProvidingAir(game.player)) {
            RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * 1.7F);
        }
    }
}

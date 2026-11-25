package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import yagen.waitmydawn.yagens_mobs.client.PetCommandControls.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class PetViewport implements Viewport {
    @Unique private Frustum yagens$view;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void yagens$captureView(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
        yagens$view = frustum;
    }

    public Frustum yagens$commandFrustum() { return yagens$view; }
}

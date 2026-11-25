package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import yagen.waitmydawn.yagens_mobs.client.PetCommandControls.Viewport;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface PetViewport extends Viewport {
    @Accessor("cullingFrustum")
    Frustum yagens$commandFrustum();
}

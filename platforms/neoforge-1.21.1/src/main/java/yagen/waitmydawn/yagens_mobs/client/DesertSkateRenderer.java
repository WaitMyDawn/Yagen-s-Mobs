package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.entity.DesertSkate;

final class DesertSkateRenderer extends GeoEntityRenderer<DesertSkate> {
    DesertSkateRenderer(EntityRendererProvider.Context context) {
        super(context, new DesertSkateModel());
        shadowRadius = 0.45F;
    }

    @Override
    public boolean shouldRender(DesertSkate skate, Frustum frustum, double x, double y, double z) {
        return (!skate.isBurrowed() || entityRenderDispatcher.shouldRenderHitBoxes())
                && super.shouldRender(skate, frustum, x, y, z);
    }

    @Override
    public void render(DesertSkate skate, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        shadowRadius = 0.45F * skate.getAgeScale();
        if (!skate.isBurrowed()) {
            withScale(skate.getAgeScale());
            super.render(skate, yaw, partialTick, poseStack, buffers, packedLight);
        }
    }
}

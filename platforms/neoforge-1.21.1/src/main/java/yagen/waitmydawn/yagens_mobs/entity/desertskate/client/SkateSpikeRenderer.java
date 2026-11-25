package yagen.waitmydawn.yagens_mobs.entity.desertskate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.SkateSpike;

public final class SkateSpikeRenderer extends EntityRenderer<SkateSpike> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "skate_spike"), "main");
    private final ModelPart spike;

    public SkateSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.spike = context.bakeLayer(LAYER);
    }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("spike", CubeListBuilder.create()
                .texOffs(100, 0).addBox(-3, -1, -1, 6, 2, 2)
                .texOffs(100, 6).addBox(3, -0.5F, -0.5F, 2, 1, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void render(SkateSpike entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        stack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        spike.render(stack, buffers.getBuffer(RenderType.entityCutout(getTextureLocation(entity))),
                light, OverlayTexture.NO_OVERLAY);
        stack.popPose();
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SkateSpike spike) {
        return DesertSkateModel.TEXTURE;
    }
}

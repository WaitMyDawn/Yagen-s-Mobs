package yagen.waitmydawn.yagens_mobs.entity.desertskate.client;

import yagen.waitmydawn.yagens_mobs.client.RenderPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.SkateSpike;

public final class SkateSpikeRenderer extends EntityRenderer<SkateSpike, SkateSpikeRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "skate_spike"), "main");
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
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SkateSpike entity, State state, float tick) {
        super.extractRenderState(entity, state, tick);
        state.yaw = Mth.lerp(tick, entity.yRotO, entity.getYRot()) - 90;
        state.pitch = Mth.lerp(tick, entity.xRotO, entity.getXRot());
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        RenderPose.rotate(poses, Axis.YP.rotationDegrees(state.yaw));
        RenderPose.rotate(poses, Axis.ZP.rotationDegrees(state.pitch));
        collector.submitModelPart(spike, poses, RenderTypes.entityCutout(DesertSkateModel.TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    static final class State extends EntityRenderState {
        private float yaw;
        private float pitch;
    }
}

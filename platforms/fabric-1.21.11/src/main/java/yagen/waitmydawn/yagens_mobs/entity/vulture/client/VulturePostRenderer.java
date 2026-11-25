package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostEntity;

public final class VulturePostRenderer implements BlockEntityRenderer<VulturePostEntity, VulturePostRenderer.State> {
    private final JoinedChestRenderer chests;

    public VulturePostRenderer(BlockEntityRendererProvider.Context context) {
        chests = new JoinedChestRenderer(context::bakeLayer);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VulturePostEntity post, State state, float tick, Vec3 camera,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(post, state, tick, camera, overlay);
        state.cargo = post.displayedCargo();
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
        var block = state.cargo;
        if (block == null) {
            return;
        }
        if (JoinedChestRenderer.isHalf(block)) {
            chests.submitHalf(block, poses, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY);
            return;
        }
        poses.pushPose();
        poses.translate(.05, .02, .05);
        poses.scale(.9F, .9F, .9F);
        nodes.submitBlock(poses, block, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private BlockState cargo;
    }
}

package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostEntity;

public final class VulturePostRenderer implements BlockEntityRenderer<VulturePostEntity> {
    private final BlockRenderDispatcher blocks;
    private final JoinedChestRenderer chests;

    public VulturePostRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
        chests = new JoinedChestRenderer(context::bakeLayer);
    }

    @Override
    public void render(VulturePostEntity station, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var cargo = station.displayedCargo();
        if (cargo != null) {
            if (JoinedChestRenderer.isHalf(cargo)) {
                chests.renderHalf(cargo, pose, buffers, light, overlay);
                return;
            }
            pose.pushPose();
            pose.translate(.05, .02, .05);
            pose.scale(.9F, .9F, .9F);
            blocks.renderSingleBlock(cargo, pose, buffers, light, overlay);
            pose.popPose();
        }
    }
}

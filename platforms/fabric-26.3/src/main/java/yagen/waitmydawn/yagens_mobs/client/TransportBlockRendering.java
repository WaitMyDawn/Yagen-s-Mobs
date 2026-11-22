package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class TransportBlockRendering {
    private TransportBlockRendering() {}

    static void submit(SubmitNodeCollector collector, PoseStack poses, BlockState state, int light) {
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        var parts = new ArrayList<BlockStateModelPart>();
        models.get(state).collectParts(RandomSource.create(42), parts);
        collector.submitBlockModel(poses, RenderTypes.cutoutMovingBlock(), parts, new int[0], light, OverlayTexture.NO_OVERLAY, 0);
    }
}

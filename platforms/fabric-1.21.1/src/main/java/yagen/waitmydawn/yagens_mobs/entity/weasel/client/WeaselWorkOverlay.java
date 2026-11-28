package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public final class WeaselWorkOverlay {
    private WeaselWorkOverlay() {}

    public static void render(WorldRenderContext context) {
        var geometry = WorkPreviewClient.geometry(Minecraft.getInstance());
        if (geometry == null) return;
        var pose = context.matrixStack();
        var camera = context.camera().getPosition();
        if (pose == null) return;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(RenderType.lines());
        var transform = pose.last();
        geometry.render((x, y, z, axis, end, color) -> line(vertices, transform, x, y, z, axis, end, color));
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose,
                             double x, double y, double z, int axis, double end, int color) {
        vertex(vertices, pose, x, y, z, axis, color);
        vertex(vertices, pose, axis == 0 ? end : x, axis == 1 ? end : y, axis == 2 ? end : z, axis, color);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               double x, double y, double z, int axis, int color) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z).setColor(color)
                .setNormal(pose, axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
    }
}

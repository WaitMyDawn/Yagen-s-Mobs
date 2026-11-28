package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "yagens_mobs", value = Dist.CLIENT)
public final class WeaselWorkOverlay {
    private WeaselWorkOverlay() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        WorkPreviewClient.tick(Minecraft.getInstance(), WeaselClient::request);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var geometry = WorkPreviewClient.geometry(Minecraft.getInstance());
        if (geometry == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
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

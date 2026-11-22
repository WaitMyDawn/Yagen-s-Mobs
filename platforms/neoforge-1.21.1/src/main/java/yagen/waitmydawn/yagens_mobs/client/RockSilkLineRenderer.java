package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkLine;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPath;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class RockSilkLineRenderer extends EntityRenderer<RockSilkLine> {
    private static final ResourceLocation SILK = RockHangerModel.id("textures/entity/rock_hanger_silk.png");
    private static final ResourceLocation IRON = RockHangerModel.id("textures/entity/rock_pin.png");
    private final ModelPart pin;

    RockSilkLineRenderer(EntityRendererProvider.Context context) {
        super(context);
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("pin", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.65F, -1.7F, -0.65F, 1.3F, 3.0F, 1.3F)
                .texOffs(8, 0).addBox(-1.55F, 0.3F, -1.55F, 3.1F, 1.25F, 3.1F), PartPose.ZERO);
        pin = LayerDefinition.create(mesh, 32, 16).bakeRoot();
    }

    @SubscribeEvent
    public static void renderOffSectionSpans(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var game = Minecraft.getInstance();
        var level = game.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        var buffers = game.renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof RockSilkLine line) || !line.active()
                    || level.isOutsideBuildHeight(line.blockPosition())
                    || event.getLevelRenderer().isSectionCompiled(line.blockPosition())) {
                continue;
            }
            // The normal entity pass skips uncompiled sections even when the long span crosses the camera frustum.
            if (!spanVisible(line, event.getFrustum())) {
                continue;
            }
            var renderer = game.getEntityRenderDispatcher().getRenderer(line);
            stack.pushPose();
            stack.translate(line.getX() - camera.x, line.getY() - camera.y, line.getZ() - camera.z);
            renderer.render(line, line.getYRot(), partial, stack, buffers, renderer.getPackedLightCoords(line, partial));
            stack.popPose();
        }
    }

    private static boolean spanVisible(RockSilkLine line, Frustum frustum) {
        return frustum.isVisible(new AABB(line.first().tip(), line.tip()).inflate(0.2));
    }

    @Override
    public boolean shouldRender(RockSilkLine line, Frustum frustum, double x, double y, double z) {
        return line.active() && (super.shouldRender(line, frustum, x, y, z)
                || spanVisible(line, frustum));
    }

    @Override
    public void render(RockSilkLine line, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        if (!line.active()) {
            return;
        }
        var first = line.first();
        var second = line.second();
        Vec3 end = line.tip().subtract(first.tip());
        if (end.lengthSqr() > 0.0001) {
            Vec3 axis = end.normalize();
            Vec3 right = axis.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = axis.cross(right);
            var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(SILK));
            int silkLight = LightTexture.pack(Math.max(5, LightTexture.block(light)), LightTexture.sky(light));
            ribbon(buffer, stack.last(), end, right.scale(0.025), up, silkLight);
            ribbon(buffer, stack.last(), end, up.scale(0.025), right, silkLight);
        }
        drawPin(first, Vec3.ZERO, stack, buffers, light);
        if (second != null) {
            drawPin(second, end, stack, buffers, light);
        }
    }

    private void drawPin(RockSilkPath.Pin endpoint, Vec3 offset, PoseStack stack, MultiBufferSource buffers, int light) {
        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        stack.mulPose(endpoint.face().getRotation());
        pin.render(stack, buffers.getBuffer(RenderType.entityCutout(IRON)), light, OverlayTexture.NO_OVERLAY);
        stack.popPose();
    }

    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 end, Vec3 width, Vec3 normal, int light) {
        vertex(buffer, pose, width.scale(-1), normal, 0, 0, light);
        vertex(buffer, pose, width, normal, 1, 0, light);
        vertex(buffer, pose, end.add(width), normal, 1, 1, light);
        vertex(buffer, pose, end.subtract(width), normal, 0, 1, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point, Vec3 normal, float u, float v, int light) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    @Override
    public ResourceLocation getTextureLocation(RockSilkLine entity) {
        return IRON;
    }
}


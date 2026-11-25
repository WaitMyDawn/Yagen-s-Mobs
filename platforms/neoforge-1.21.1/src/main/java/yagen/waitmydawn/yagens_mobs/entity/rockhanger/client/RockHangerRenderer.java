package yagen.waitmydawn.yagens_mobs.entity.rockhanger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger;

public final class RockHangerRenderer extends GeoEntityRenderer<RockHanger> {
    private static final ResourceLocation SILK = RockHangerModel.id("textures/entity/rock_hanger_silk.png");
    private static final double MODEL_CENTER_Y = 18.9 / 16.0;

    private Matrix4f inverseEntityPose;
    private Vec3 attachment;

    public RockHangerRenderer(EntityRendererProvider.Context context) {
        super(context, new RockHangerModel());
    }

    @Override
    public boolean shouldRender(RockHanger mob, Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(mob, frustum, x, y, z)) {
            return true;
        }
        return mob.anchor().isPresent() && frustum.isVisible(new AABB(mob.position(), mob.silkTop()).inflate(0.12));
    }

    @Override
    protected void applyRotations(RockHanger mob, PoseStack stack, float age, float yaw, float partialTick, float scale) {
        stack.translate(0, mob.getBbHeight() / 2, 0);
        var rotation = mob.renderPose(partialTick);
        stack.mulPose(rotation);
        if (mob.phase() == RockHanger.Phase.CLIMBING || mob.phase() == RockHanger.Phase.BRACED) {
            // The upright collision box is wider than the shell is deep. Seat the hooks against the support.
            float vertical = Math.abs(rotation.transform(new Vector3f(0, 1, 0)).y);
            stack.translate(0, Mth.lerp(vertical, 0.29F, 0.08F), 0);
        }
        if (mob.deathTime > 0) {
            float progress = Mth.clamp((mob.deathTime + partialTick) / 20, 0, 1);
            stack.mulPose(Axis.ZP.rotationDegrees(90 * progress));
        }
        if (mob.shieldingTether() && mob.hurtTime > 0) {
            float recovery = Math.max(0, mob.hurtTime - partialTick) / 10.0F;
            stack.mulPose(Axis.ZP.rotation(0.24F * recovery * Mth.sin(recovery * 7)));
        }
        stack.translate(0, -MODEL_CENTER_Y - 0.01, 0);
    }

    @Override
    public void render(RockHanger mob, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        boolean showSilk = mob.anchor().isPresent() && mob.phase() != RockHanger.Phase.BRACED && !mob.isInvisible();
        inverseEntityPose = showSilk ? new Matrix4f(stack.last().pose()).invert() : null;
        attachment = null;
        try {
            super.render(mob, yaw, partialTick, stack, buffers, light);
            if (attachment != null) {
                Vec3 base = new Vec3(Mth.lerp(partialTick, mob.xOld, mob.getX()),
                        Mth.lerp(partialTick, mob.yOld, mob.getY()), Mth.lerp(partialTick, mob.zOld, mob.getZ()));
                Vec3 end = mob.silkTop().subtract(base);
                drawSilk(stack, buffers, light, attachment, end);
            }
        } finally {
            inverseEntityPose = null;
            attachment = null;
        }
    }

    @Override
    public void renderRecursively(PoseStack stack, RockHanger mob, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer consumer, boolean reRender,
                                  float partialTick, int light, int overlay, int color) {
        if (bone.getName().equals("silk_attachment")) {
            if (inverseEntityPose != null && !reRender) {
                // Capture the socket after its parent animations and the hit rotation have been applied.
                stack.pushPose();
                RenderUtil.prepMatrixForBone(stack, bone);
                RenderUtil.translateToPivotPoint(stack, bone);
                var knot = stack.last().pose().transformPosition(new Vector3f());
                inverseEntityPose.transformPosition(knot);
                attachment = new Vec3(knot.x, knot.y, knot.z);
                stack.popPose();
            }
            return;
        }
        super.renderRecursively(stack, mob, bone, type, buffers, consumer, reRender, partialTick, light, overlay, color);
    }

    private static void drawSilk(PoseStack stack, MultiBufferSource buffers, int light, Vec3 bottom, Vec3 top) {
        Vec3 span = top.subtract(bottom);
        if (span.lengthSqr() < 0.01) {
            return;
        }
        Vec3 axis = span.normalize();
        Vec3 across = axis.cross(new Vec3(0, 0, 1));
        across = across.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : across.normalize();
        Vec3 cross = axis.cross(across);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(SILK));
        int silkLight = LightTexture.pack(Math.max(6, LightTexture.block(light)), LightTexture.sky(light));
        ribbon(buffer, stack.last(), bottom, top, across.scale(0.032), silkLight);
        ribbon(buffer, stack.last(), bottom, top, cross.scale(0.032), silkLight);
    }

    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 bottom, Vec3 top, Vec3 width, int light) {
        point(buffer, pose, bottom.subtract(width), 0, 1, light);
        point(buffer, pose, bottom.add(width), 1, 1, light);
        point(buffer, pose, top.add(width), 1, 0, light);
        point(buffer, pose, top.subtract(width), 0, 0, light);
    }

    private static void point(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos, float u, float v, int light) {
        vertex(buffer, pose, (float) pos.x, (float) pos.y, (float) pos.z, u, v, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light) {
        buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}


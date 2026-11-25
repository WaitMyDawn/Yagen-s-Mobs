package yagen.waitmydawn.yagens_mobs.entity.rockhanger.client;

import yagen.waitmydawn.yagens_mobs.client.GeckoLivingRenderer;
import yagen.waitmydawn.yagens_mobs.client.RenderPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger;

public final class RockHangerRenderer extends GeckoLivingRenderer<RockHanger> {
    private static final Identifier SILK = RockHangerModel.id("textures/entity/rock_hanger_silk.png");
    private static final double MODEL_CENTER_Y = 18.9 / 16.0;
    private static final DataTicket<Posture> POSTURE = DataTicket.create("yagens_mobs:hanger_posture", Posture.class);

    public RockHangerRenderer(EntityRendererProvider.Context context) {
        super(context, new RockHangerModel());
    }

    @Override
    public boolean shouldRender(RockHanger mob, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(mob, frustum, x, y, z)
                || mob.anchor().isPresent() && frustum.isVisible(new AABB(mob.position(), mob.silkTop()).inflate(.12));
    }

    @Override
    public void addRenderData(RockHanger mob, Void related, State state, float tick) {
        super.addRenderData(mob, related, state, tick);
        var rotation = mob.renderPose(tick);
        float shim = 0;
        if (mob.phase() == RockHanger.Phase.CLIMBING || mob.phase() == RockHanger.Phase.BRACED) {
            shim = Mth.lerp(Math.abs(rotation.transform(new Vector3f(0, 1, 0)).y), .29F, .08F);
        }
        float death = mob.deathTime <= 0 ? 0 : 90 * Mth.clamp((mob.deathTime + tick) / 20, 0, 1);
        float hit = mob.shieldingTether() ? Math.max(0, mob.hurtTime - tick) / 10F : 0;
        boolean tether = mob.anchor().isPresent() && mob.phase() != RockHanger.Phase.BRACED && !mob.isInvisible();
        var base = new Vec3(state.x, state.y, state.z);
        state.addGeckolibData(POSTURE, new Posture(rotation, mob.getBbHeight() / 2, shim, death, hit, mob.isTuftClipped(),
                tether ? mob.silkBottom().subtract(mob.position()) : null, tether ? mob.silkTop().subtract(base) : null));
    }

    @Override
    protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        var pose = pass.getGeckolibData(POSTURE);
        poses.translate(0, pose.halfHeight(), 0);
        RenderPose.rotate(poses, pose.rotation());
        poses.translate(0, pose.shim(), 0);
        if (pose.death() != 0) {
            RenderPose.rotate(poses, Axis.ZP.rotationDegrees(pose.death()));
        }
        RenderPose.rotate(poses, Axis.ZP.rotation(.24F * pose.hit() * Mth.sin(pose.hit() * 7)));
        poses.translate(0, -MODEL_CENTER_Y - .01, 0);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        bones.get("body").ifPresent(bone -> bone.setTranslateY(0));
        bones.get("tether").ifPresent(bone -> bone.skipRender(true));
        bones.get("silk_tuft").ifPresent(bone -> bone.skipRender(pass.getGeckolibData(POSTURE).clipped()));
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        var pose = state.getGeckolibData(POSTURE);
        if (pose.bottom() == null) {
            return;
        }
        var bottom = pose.bottom();
        var top = pose.top();
        var span = top.subtract(bottom);
        if (span.lengthSqr() < .01) {
            return;
        }
        var axis = span.normalize();
        var radial = axis.cross(new Vec3(0, 0, 1));
        var across = radial.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : radial.normalize();
        var cross = axis.cross(across);
        int light = RenderPose.minimumBlockLight(state.getPackedLight(), 6);
        collector.submitCustomGeometry(poses, RenderTypes.entityCutoutNoCull(SILK), (transform, buffer) -> {
            ribbon(buffer, transform, bottom, top, across.scale(.032), light);
            ribbon(buffer, transform, bottom, top, cross.scale(.032), light);
        });
    }

    private record Posture(Quaternionf rotation, float halfHeight, float shim, float death, float hit,
                           boolean clipped, Vec3 bottom, Vec3 top) {}

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


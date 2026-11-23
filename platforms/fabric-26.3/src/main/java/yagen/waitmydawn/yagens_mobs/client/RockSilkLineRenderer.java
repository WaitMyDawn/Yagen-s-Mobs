package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkLine;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPath;

public final class RockSilkLineRenderer extends EntityRenderer<RockSilkLine, RockSilkLineRenderer.State> {
    private static final Identifier SILK = RockHangerModel.id("textures/entity/rock_hanger_silk.png");
    private static final Identifier IRON = RockHangerModel.id("textures/entity/rock_pin.png");
    private final ModelPart pin;

    RockSilkLineRenderer(EntityRendererProvider.Context context) {
        super(context);
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("pin", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.65F, -1.7F, -0.65F, 1.3F, 3.0F, 1.3F)
                .texOffs(8, 0).addBox(-1.55F, 0.3F, -1.55F, 3.1F, 1.25F, 3.1F), PartPose.ZERO);
        pin = LayerDefinition.create(mesh, 32, 16).bakeRoot();
    }

    public static void renderOffSectionSpans(LevelRenderContext context) {
        var game = Minecraft.getInstance();
        if (game.level == null) {
            return;
        }
        var camera = context.levelState().cameraRenderState;
        var dispatcher = game.getEntityRenderDispatcher();
        float tick = game.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var entity : game.level.entitiesForRendering()) {
            if (!(entity instanceof RockSilkLine line) || !line.active()
                    || game.level.isOutsideBuildHeight(line.blockPosition())
                    || game.levelRenderer.isSectionCompiledAndVisible(line.blockPosition(), game.level.getGameTime())
                    || !spanNearby(line, camera.pos)) {
                continue;
            }
            var state = dispatcher.extractEntity(line, tick);
            dispatcher.submit(state, camera, state.x - camera.pos.x, state.y - camera.pos.y, state.z - camera.pos.z,
                    context.poseStack(), context.submitNodeCollector());
        }
    }

    private static boolean spanNearby(RockSilkLine line, Vec3 camera) {
        Vec3 from = line.first().tip();
        Vec3 span = line.tip().subtract(from);
        double fraction = span.lengthSqr() < 1.e-8 ? 0
                : Math.clamp(camera.subtract(from).dot(span) / span.lengthSqr(), 0, 1);
        return camera.distanceToSqr(from.add(span.scale(fraction))) <= 128 * 128;
    }

    private static boolean spanVisible(RockSilkLine line, Frustum frustum) {
        return frustum.isVisible(new AABB(line.first().tip(), line.tip()).inflate(.2));
    }

    @Override
    public boolean shouldRender(RockSilkLine line, Frustum frustum, double x, double y, double z, float tick) {
        return line.active() && (super.shouldRender(line, frustum, x, y, z, tick) || spanVisible(line, frustum));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RockSilkLine line, State state, float tick) {
        super.extractRenderState(line, state, tick);
        if (line.active()) {
            state.first = line.first();
            state.second = line.second();
            state.end = line.tip().subtract(state.first.tip());
        }
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.first == null) {
            return;
        }
        var end = state.end;
        if (end.lengthSqr() > .0001) {
            Vec3 axis = end.normalize();
            Vec3 radial = axis.cross(new Vec3(0, 1, 0));
            Vec3 right = radial.lengthSqr() < .001 ? new Vec3(1, 0, 0) : radial.normalize();
            Vec3 up = axis.cross(right);
            int light = RenderPose.minimumBlockLight(state.lightCoords, 5);
            collector.submitCustomGeometry(poses, RenderTypes.entityCutout(SILK), (transform, buffer) -> {
                ribbon(buffer, transform, end, right.scale(.025), up, light);
                ribbon(buffer, transform, end, up.scale(.025), right, light);
            });
        }
        drawPin(state.first, Vec3.ZERO, poses, collector, state.lightCoords);
        if (state.second != null) {
            drawPin(state.second, end, poses, collector, state.lightCoords);
        }
        super.submit(state, poses, collector, camera);
    }

    private void drawPin(RockSilkPath.Pin endpoint, Vec3 offset, PoseStack stack, SubmitNodeCollector collector, int light) {
        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        RenderPose.rotate(stack, endpoint.face().getRotation());
        collector.submitModelPart(pin, stack, RenderTypes.entityCutout(IRON), light, OverlayTexture.NO_OVERLAY, null);
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

    static final class State extends EntityRenderState {
        private RockSilkPath.Pin first;
        private RockSilkPath.Pin second;
        private Vec3 end;
    }
}

package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRay;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRays;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageTailPose;

public final class SalvageRayRenderer extends GeckoLivingRenderer<SalvageRay> {
    private static final DataTicket<Pose> FLIGHT = DataTicket.create("yagens_mobs:salvage_flight", Pose.class);
    private static final List<String> CHAINS = List.of("left", "center", "right");
    private static final String[][] TAILS = new String[CHAINS.size()][SalvageTailPose.LINKS];

    static {
        for (int chain = 0; chain < TAILS.length; chain++) {
            for (int joint = 0; joint < TAILS[chain].length; joint++) {
                TAILS[chain][joint] = "tail_" + CHAINS.get(chain) + "_0" + (joint + 1);
            }
        }
    }

    private SalvageRayRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
    }

    public static void register() {
        EntityRendererRegistry.register(SalvageRays.RAY.get(), SalvageRayRenderer::new);
    }

    @Override
    public void addRenderData(SalvageRay ray, Void related, State state, float tick) {
        super.addRenderData(ray, related, state, tick);
        var cargo = new ItemStackRenderState();
        if (!ray.cargo().isEmpty()) {
            itemModelResolver.updateForLiving(cargo, ray.cargo(), ItemDisplayContext.GROUND, ray);
        }
        float[] tail = new float[CHAINS.size() * SalvageTailPose.LINKS * 3];
        for (int chain = 0; chain < TAILS.length; chain++) {
            for (int joint = 0; joint < TAILS[chain].length; joint++) {
                int index = (chain * SalvageTailPose.LINKS + joint) * 3;
                for (int axis = 0; axis < 3; axis++) {
                    tail[index + axis] = ray.tailPose().rotation(chain, joint, axis, tick);
                }
            }
        }
        state.addGeckolibData(FLIGHT, new Pose(Mth.rotLerp(tick, ray.yRotO, ray.getYRot()),
                ray.isAlive() ? ray.flightPitch(tick) : 0, ray.isAlive() ? ray.flightBank(tick) : 0,
                ray.getAgeScale(), tail, cargo));
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> pass, float width, float height) {
        float scale = pass.getGeckolibData(FLIGHT).scale();
        super.scaleModelForRender(pass, width * scale, height * scale);
    }

    @Override
    protected float getShadowRadius(State state) {
        return .54F * state.getGeckolibData(FLIGHT).scale();
    }

    @Override
    protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        var pose = pass.getGeckolibData(FLIGHT);
        super.applyRotations(pass, poses, pose.yaw());
        poses.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
        poses.mulPose(Axis.ZP.rotationDegrees(pose.bank()));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        float[] tail = pass.getGeckolibData(FLIGHT).tail();
        for (int chain = 0; chain < TAILS.length; chain++) {
            for (int joint = 0; joint < TAILS[chain].length; joint++) {
                int index = (chain * SalvageTailPose.LINKS + joint) * 3;
                bones.get(TAILS[chain][joint]).ifPresent(bone -> bone.setRotation(
                        bone.getRotX() + tail[index], bone.getRotY() + tail[index + 1], bone.getRotZ() + tail[index + 2]));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
        super.submit(state, poses, nodes, camera);
        var pose = state.getGeckolibData(FLIGHT);
        if (!pose.cargo().isEmpty()) {
            Vec3 forward = Vec3.directionFromRotation(-pose.pitch(), pose.yaw()).scale(.68);
            poses.pushPose();
            poses.translate(forward.x, .11 + forward.y, forward.z);
            poses.mulPose(Axis.YP.rotationDegrees(180 - pose.yaw()));
            poses.scale(.72F, .72F, .72F);
            pose.cargo().submit(poses, nodes, state.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
        }
    }

    @Override
    public RenderType getRenderType(State state, Identifier texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }

    private record Pose(float yaw, float pitch, float bank, float scale, float[] tail, ItemStackRenderState cargo) {}

    private static final class Model extends GeoAppearance<SalvageRay> {
        private static final List<Identifier> MODELS = List.of(SalvageRays.id("entity/salvage_ray_1_tail"),
                SalvageRays.id("entity/salvage_ray_2_tail"), SalvageRays.id("entity/salvage_ray_3_tail"));
        private static final Map<String, Identifier> TEXTURES = SalvageRay.COLORS.stream()
                .collect(Collectors.toUnmodifiableMap(color -> color,
                        color -> SalvageRays.id("textures/entity/salvage_ray_" + color + ".png")));

        @Override
        public Identifier selectModel(SalvageRay ray) { return MODELS.get(ray.tails() - 1); }

        @Override
        public Identifier selectTexture(SalvageRay ray) { return TEXTURES.get(ray.color()); }

        @Override
        public Identifier getAnimationResource(SalvageRay ray) { return SalvageRays.id("entity/salvage_ray"); }
    }
}

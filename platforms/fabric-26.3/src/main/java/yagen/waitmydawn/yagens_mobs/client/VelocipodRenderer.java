package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;

final class VelocipodRenderer extends GeckoLivingRenderer<Velocipod> {
    private static final DataTicket<RidePose> RIDE = DataTicket.create("yagens_mobs:velocipod_ride", RidePose.class);

    VelocipodRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(Velocipod.MODEL_SCALE);
    }

    @Override
    public void addRenderData(Velocipod pod, Void related, State state, float tick) {
        super.addRenderData(pod, related, state, tick);
        if (pod.isVehicle() && pod.isAlive()) {
            state.addGeckolibData(RIDE, new RidePose(Mth.rotLerp(tick, pod.yRotO, pod.getYRot()),
                    pod.riderTilt(tick), Mth.clamp(pod.riderPitch(tick) / 20, 0, 1)));
        }
    }

    @Override
    protected float getShadowRadius(State state) {
        return state.isBaby ? .32F : .56F;
    }

    @Override
    protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        var ride = pass.getGeckolibData(RIDE);
        super.applyRotations(pass, poses, ride == null ? yaw : ride.yaw());
        if (ride != null) {
            float y = Velocipod.SEAT_HEIGHT / 16;
            float z = Velocipod.SEAT_BACK / 16;
            poses.translate(0, y, z);
            RenderPose.rotate(poses, ride.tilt());
            poses.translate(0, -y, -z);
        }
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        var ride = pass.getGeckolibData(RIDE);
        if (ride == null) {
            return;
        }
        // The mount and rider share the seat pose. Root clip motion would move the model away from the rider.
        bones.get("root").ifPresent(bone -> bone.setTranslation(0, 0, 0).setRotation(0, 0, 0));
        float climb = ride.climb();
        bones.get("abdomen").ifPresent(bone -> bone.setRotX(bone.getRotX() - .18F * climb));
        bones.get("left_leg_hind_tip").ifPresent(bone -> bone.setRotX(bone.getRotX() + .24F * climb));
        bones.get("right_leg_hind_tip").ifPresent(bone -> bone.setRotX(bone.getRotX() + .24F * climb));
    }

    @Override
    public RenderType getRenderType(State state, Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    private record RidePose(float yaw, Quaternionf tilt, float climb) {}

    private static final class Model extends GeoAppearance<Velocipod> {
        private static final Identifier ADULT = id("entity/velocipod");
        private static final Identifier JUVENILE = id("entity/velocipod_juvenile");
        private static final Identifier ADULT_ANIMATION = id("entity/velocipod");
        private static final Identifier YOUNG_ANIMATION = id("entity/velocipod_juvenile");
        private final Identifier[] textures = java.util.Arrays.stream(Velocipod.Color.values())
                .map(color -> id("textures/entity/velocipod/" + color.id() + ".png"))
                .toArray(Identifier[]::new);

        @Override
        public Identifier selectModel(Velocipod pod) {
            return pod.isBaby() ? JUVENILE : ADULT;
        }

        @Override
        public Identifier selectTexture(Velocipod pod) {
            return textures[pod.color().ordinal()];
        }

        @Override
        public Identifier getAnimationResource(Velocipod pod) {
            return pod.isBaby() ? YOUNG_ANIMATION : ADULT_ANIMATION;
        }

        private static Identifier id(String path) {
            return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
        }
    }
}


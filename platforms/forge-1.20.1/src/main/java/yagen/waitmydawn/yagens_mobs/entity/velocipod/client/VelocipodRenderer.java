package yagen.waitmydawn.yagens_mobs.entity.velocipod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

public final class VelocipodRenderer extends GeoEntityRenderer<Velocipod> {
    public VelocipodRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(Velocipod.MODEL_SCALE);
    }

    @Override
    public void render(Velocipod pod, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        shadowRadius = pod.isBaby() ? .32F : .56F;
        super.render(pod, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    protected void applyRotations(Velocipod pod, PoseStack poses, float age, float yaw,
                                  float partialTick) {
        super.applyRotations(pod, poses, age,
                pod.isVehicle() ? Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot()) : yaw, partialTick);
        if (pod.isVehicle() && pod.isAlive()) {
            // GeckoLib has already applied the model scale. Pivot around the physical passenger attachment.
            float seatHeight = Velocipod.SEAT_HEIGHT / 16;
            float seatBack = Velocipod.SEAT_BACK / 16;
            poses.translate(0, seatHeight, seatBack);
            poses.mulPose(pod.riderTilt(partialTick));
            poses.translate(0, -seatHeight, -seatBack);
        }
    }

    @Override
    public RenderType getRenderType(Velocipod pod, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    private static final class Model extends GeoModel<Velocipod> {
        private static final ResourceLocation ADULT = id("geo/entity/velocipod.geo.json");
        private static final ResourceLocation JUVENILE = id("geo/entity/velocipod_juvenile.geo.json");
        private static final ResourceLocation ADULT_ANIMATION = id("animations/entity/velocipod.animation.json");
        private static final ResourceLocation YOUNG_ANIMATION = id("animations/entity/velocipod_juvenile.animation.json");
        private final ResourceLocation[] textures = java.util.Arrays.stream(Velocipod.Color.values())
                .map(color -> id("textures/entity/velocipod/" + color.id() + ".png"))
                .toArray(ResourceLocation[]::new);

        @Override
        public ResourceLocation getModelResource(Velocipod pod) {
            return pod.isBaby() ? JUVENILE : ADULT;
        }

        @Override
        public ResourceLocation getTextureResource(Velocipod pod) {
            return textures[pod.color().ordinal()];
        }

        @Override
        public ResourceLocation getAnimationResource(Velocipod pod) {
            return pod.isBaby() ? YOUNG_ANIMATION : ADULT_ANIMATION;
        }

        @Override
        public void setCustomAnimations(Velocipod pod, long instance, AnimationState<Velocipod> state) {
            super.setCustomAnimations(pod, instance, state);
            if (pod.isVehicle()) {
                getBone("root").ifPresent(root -> {
                    // Ridden root motion is applied by the shared seat transform; clips still animate the limbs.
                    root.setPosX(0);
                    root.setPosY(0);
                    root.setPosZ(0);
                    root.setRotX(0);
                    root.setRotY(0);
                    root.setRotZ(0);
                });
                float climb = Mth.clamp(pod.riderPitch(state.getPartialTick()) / 20, 0, 1);
                if (climb > 0) {
                    getBone("abdomen").ifPresent(bone -> bone.setRotX(bone.getRotX() - .18F * climb));
                    tuckHindLeg("left_leg_hind_tip", climb);
                    tuckHindLeg("right_leg_hind_tip", climb);
                }
            }
        }

        private void tuckHindLeg(String name, float climb) {
            getBone(name).ifPresent(bone -> bone.setRotX(bone.getRotX() + .24F * climb));
        }

        private static ResourceLocation id(String path) {
            return new ResourceLocation(ModIdentity.ID, path);
        }
    }
}


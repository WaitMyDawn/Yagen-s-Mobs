package yagen.waitmydawn.yagens_mobs.salvage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRay;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRays;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageTailPose;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class SalvageRayRenderer extends GeoEntityRenderer<SalvageRay> {
    private SalvageRayRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .54F;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SalvageRays.RAY.get(), SalvageRayRenderer::new);
    }

    @Override
    protected void applyRotations(SalvageRay ray, PoseStack poses, float age, float yaw, float partial, float scale) {
        super.applyRotations(ray, poses, age, Mth.rotLerp(partial, ray.yRotO, ray.getYRot()), partial, scale);
        if (ray.isAlive()) {
            poses.mulPose(Axis.XP.rotationDegrees(ray.flightPitch(partial)));
            poses.mulPose(Axis.ZP.rotationDegrees(ray.flightBank(partial)));
        }
    }

    @Override
    public void render(SalvageRay ray, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        float scale = ray.getAgeScale();
        shadowRadius = .54F * scale;
        poses.pushPose();
        poses.scale(scale, scale, scale);
        super.render(ray, yaw, partial, poses, buffers, light);
        poses.popPose();
        if (!ray.cargo().isEmpty()) {
            Vec3 forward = Vec3.directionFromRotation(-ray.flightPitch(partial), Mth.rotLerp(partial, ray.yRotO, ray.getYRot()));
            poses.pushPose();
            poses.translate(forward.x * .68, .11 + forward.y * .68, forward.z * .68);
            poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            poses.scale(.72F, .72F, .72F);
            Minecraft.getInstance().getItemRenderer().renderStatic(ray.cargo(), ItemDisplayContext.GROUND,
                    light, OverlayTexture.NO_OVERLAY, poses, buffers, ray.level(), ray.getId());
            poses.popPose();
        }
    }

    @Override
    public RenderType getRenderType(SalvageRay ray, ResourceLocation texture, MultiBufferSource buffers, float partial) {
        return RenderType.entityCutoutNoCull(texture);
    }

    private static final class Model extends GeoModel<SalvageRay> {
        private static final List<ResourceLocation> GEOMETRIES = List.of(
                SalvageRays.id("geo/entity/salvage_ray_1_tail.geo.json"),
                SalvageRays.id("geo/entity/salvage_ray_2_tail.geo.json"),
                SalvageRays.id("geo/entity/salvage_ray_3_tail.geo.json"));
        private static final ResourceLocation FLIGHT = SalvageRays.id("animations/entity/salvage_ray.animation.json");
        private static final Map<String, ResourceLocation> TEXTURES = SalvageRay.COLORS.stream()
                .collect(Collectors.toUnmodifiableMap(c -> c, c -> SalvageRays.id("textures/entity/salvage_ray_" + c + ".png")));
        private static final String[][] TAILS = new String[3][SalvageTailPose.LINKS];

        static {
            List<String> chains = List.of("left", "center", "right");
            for (int chain = 0; chain < TAILS.length; chain++) {
                for (int joint = 0; joint < TAILS[chain].length; joint++) {
                    TAILS[chain][joint] = "tail_" + chains.get(chain) + "_0" + (joint + 1);
                }
            }
        }

        @Override
        public ResourceLocation getModelResource(SalvageRay ray) { return GEOMETRIES.get(ray.tails() - 1); }

        @Override
        public ResourceLocation getTextureResource(SalvageRay ray) { return TEXTURES.get(ray.color()); }

        @Override
        public ResourceLocation getAnimationResource(SalvageRay ray) { return FLIGHT; }

        @Override
        public void setCustomAnimations(SalvageRay ray, long instance, AnimationState<SalvageRay> state) {
            super.setCustomAnimations(ray, instance, state);
            var pose = ray.tailPose();
            float part = state.getPartialTick();
            for (int chain = 0; chain < TAILS.length; chain++) {
                for (int joint = 0; joint < TAILS[chain].length; joint++) {
                    var bone = getBone(TAILS[chain][joint]).orElse(null);
                    if (bone == null) {
                        continue;
                    }
                    var rest = bone.getInitialSnapshot();
                    bone.setRotX(rest.getRotX() + pose.rotation(chain, joint, 0, part));
                    bone.setRotY(rest.getRotY() + pose.rotation(chain, joint, 1, part));
                    bone.setRotZ(rest.getRotZ() + pose.rotation(chain, joint, 2, part));
                }
            }
        }
    }
}

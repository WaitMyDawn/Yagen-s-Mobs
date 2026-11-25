package yagen.waitmydawn.yagens_mobs.entity.butterfly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterfly;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class LumenRenderer extends GeoEntityRenderer<LumenButterfly> {
    private static final ResourceLocation EMISSIVE = id("textures/entity/lumen_butterfly_adult_emissive.png");
    private static final ResourceLocation BUBBLE = id("textures/entity/lumen_butterfly_water_bubble.png");

    private LumenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(.5F);
        shadowRadius = .085F;
        addRenderLayer(new Effects(this));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LumenButterflies.TYPE.get(), LumenRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(LumenButterflies.EXPERIENCE.get(), sprites ->
                (option, level, x, y, z, dx, dy, dz) -> LumenSpark.create(option, level, x, y, z, dx, dy, dz, sprites));
    }

    @Override
    public RenderType getRenderType(LumenButterfly mob, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    protected void applyRotations(LumenButterfly mob, PoseStack poses, float age, float yaw, float partialTick, float scale) {
        super.applyRotations(mob, poses, age, yaw, partialTick, scale);
        if (mob.isAlive() && mob.fliesWithOwner()) {
            poses.translate(0, .12, 0);
            poses.mulPose(Axis.XP.rotationDegrees(-mob.getViewXRot(partialTick)));
            poses.translate(0, -.12, 0);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    private static final class Model extends GeoModel<LumenButterfly> {
        private static String model(LumenButterfly mob) {
            return "lumen_butterfly_" + (mob.isBaby() ? "juvenile" : "adult");
        }

        @Override
        public ResourceLocation getModelResource(LumenButterfly mob) {
            return id("geo/entity/" + model(mob) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(LumenButterfly mob) {
            return id("textures/entity/" + model(mob) + "_" + (mob.isRainbow() ? "rainbow" : mob.color().id()) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(LumenButterfly mob) {
            return id("animations/entity/" + model(mob) + ".animation.json");
        }
    }

    private static final class Effects extends GeoRenderLayer<LumenButterfly> {
        private static final float RADIUS = .42F;
        private static final float CENTER = .225F;

        private Effects(LumenRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poses, LumenButterfly mob, BakedGeoModel model, RenderType base,
                           MultiBufferSource buffers, VertexConsumer vertices, float partialTick, int light, int overlay) {
            if (mob.hasStoredXp() && !mob.isBaby()) {
                RenderType glow = RenderType.eyes(EMISSIVE);
                getRenderer().reRender(model, poses, buffers, mob, glow, buffers.getBuffer(glow), partialTick,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            }
            if (mob.isRainbow()) {
                RenderType glint = RenderType.entityGlint();
                getRenderer().reRender(model, poses, buffers, mob, glint, buffers.getBuffer(glint), partialTick,
                        light, overlay, -1);
            }
            if (!mob.inBubble()) {
                return;
            }
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucentCull(BUBBLE));
            poses.pushPose();
            poses.translate(0, mob.isBaby() ? .12 : CENTER, 0);
            if (mob.isBaby()) {
                poses.scale(.64F, .64F, .64F);
            }
            for (Direction face : Direction.values()) {
                poses.pushPose();
                poses.mulPose(face.getRotation());
                PoseStack.Pose pose = poses.last();
                vertex(glass, pose, -RADIUS, RADIUS, 0, 0, light);
                vertex(glass, pose, RADIUS, RADIUS, 1, 0, light);
                vertex(glass, pose, RADIUS, -RADIUS, 1, 1, light);
                vertex(glass, pose, -RADIUS, -RADIUS, 0, 1, light);
                poses.popPose();
            }
            poses.popPose();
        }

        private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float z, int u, int v, int light) {
            consumer.addVertex(pose, x, RADIUS, z).setColor(255, 255, 255, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        }
    }
}

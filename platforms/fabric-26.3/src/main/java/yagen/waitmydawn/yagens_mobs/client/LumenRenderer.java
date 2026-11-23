package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenButterfly;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

final class LumenRenderer extends GeckoLivingRenderer<LumenButterfly> {
    private static final Identifier EMISSIVE = id("textures/entity/lumen_butterfly_adult_emissive.png");
    private static final Identifier BUBBLE = id("textures/entity/lumen_butterfly_water_bubble.png");
    private static final DataTicket<EffectsState> EFFECTS = DataTicket.create("yagens_mobs:lumen_effects", EffectsState.class);

    private LumenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(.5F);
        withRenderLayer(new Effects(this));
    }

    static void register() {
        EntityRendererRegistry.register(LumenButterflies.TYPE.get(), LumenRenderer::new);
        ParticleProviderRegistry.getInstance().register(LumenButterflies.EXPERIENCE.get(), sprites ->
                (option, level, x, y, z, dx, dy, dz, random) ->
                        LumenSpark.create(option, level, x, y, z, dx, dy, dz, random, sprites));
    }

    @Override
    public void addRenderData(LumenButterfly mob, Void related, State state, float tick) {
        super.addRenderData(mob, related, state, tick);
        state.addGeckolibData(EFFECTS, new EffectsState(!mob.isBaby() && mob.hasStoredXp(),
                mob.isRainbow(), mob.inBubble(), mob.isAlive() && mob.fliesWithOwner() ? mob.getViewXRot(tick) : 0));
    }

    @Override
    protected float getShadowRadius(State state) {
        return .085F;
    }

    @Override
    protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        super.applyRotations(pass, poses, yaw);
        float pitch = pass.getGeckolibData(EFFECTS).pitch();
        if (pitch != 0) {
            poses.translate(0, .12, 0);
            RenderPose.rotate(poses, Axis.XP.rotationDegrees(-pitch));
            poses.translate(0, -.12, 0);
        }
    }

    @Override
    public RenderType getRenderType(State state, Identifier texture) {
        return state.getGeckolibData(EFFECTS).rainbow() ? RenderTypes.entitySolidGlint(texture) : RenderTypes.entityCutout(texture);
    }

    private record EffectsState(boolean glow, boolean rainbow, boolean bubble, float pitch) {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    private static final class Model extends GeoAppearance<LumenButterfly> {
        private static String model(LumenButterfly mob) {
            return "lumen_butterfly_" + (mob.isBaby() ? "juvenile" : "adult");
        }

        @Override
        public Identifier selectModel(LumenButterfly mob) {
            return id("entity/" + model(mob));
        }

        @Override
        public Identifier selectTexture(LumenButterfly mob) {
            return id("textures/entity/" + model(mob) + "_" + (mob.isRainbow() ? "rainbow" : mob.color().id()) + ".png");
        }

        @Override
        public Identifier getAnimationResource(LumenButterfly mob) {
            return id("entity/" + model(mob));
        }
    }

    private static final class Effects extends GeoRenderLayer<LumenButterfly, Void, State> {
        private static final float RADIUS = .42F;

        private Effects(LumenRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submitRenderTask(RenderPassInfo<State> pass, SubmitNodeCollector collector) {
            if (!pass.willRender()) {
                return;
            }
            EffectsState state = pass.getGeckolibData(EFFECTS);
            if (state.glow()) {
                getRenderer().submitRenderTasks(pass, collector, RenderTypes.eyes(EMISSIVE));
            }

            if (state.bubble()) {
                renderBubble(pass, collector);
            }
        }

        private static void renderBubble(RenderPassInfo<State> pass, SubmitNodeCollector collector) {
            PoseStack poses = pass.poseStack();
            poses.pushPose();
            boolean baby = pass.renderState().isBaby;
            poses.translate(0, baby ? .12 : .225, 0);
            if (baby) {
                poses.scale(.64F, .64F, .64F);
            }
            for (Direction face : Direction.values()) {
                poses.pushPose();
                RenderPose.rotate(poses, face.getRotation());
                int light = pass.packedLight();
                collector.submitCustomGeometry(poses, RenderTypes.entityTranslucentCull(BUBBLE), (pose, vertices) -> {
                    vertex(vertices, pose, -RADIUS, RADIUS, 0, 0, light);
                    vertex(vertices, pose, RADIUS, RADIUS, 1, 0, light);
                    vertex(vertices, pose, RADIUS, -RADIUS, 1, 1, light);
                    vertex(vertices, pose, -RADIUS, -RADIUS, 0, 1, light);
                });
                poses.popPose();
            }
            poses.popPose();
        }

        private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float z, int u, int v, int light) {
            buffer.addVertex(pose, x, RADIUS, z).setColor(255, 255, 255, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        }
    }
}

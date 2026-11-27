package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;

public final class BubbleSalamanderRenderer extends GeoEntityRenderer<BubbleSalamander> {
    public BubbleSalamanderRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .34F;
    }

    @Override
    public RenderType getRenderType(BubbleSalamander mob, ResourceLocation texture, MultiBufferSource buffers,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRender(BubbleSalamander mob, Frustum frustum, double x, double y, double z) {
        return !mob.isWorn() && super.shouldRender(mob, frustum, x, y, z);
    }

    @Override
    public boolean shouldShowName(BubbleSalamander mob) {
        return !mob.isWorn() && super.shouldShowName(mob);
    }

    @Override
    public void render(BubbleSalamander mob, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        withScale(mob.getAgeScale());
        shadowRadius = .34F * mob.getAgeScale();
        super.render(mob, yaw, partialTick, poses, buffers, light);
    }

    @Override
    protected void applyRotations(BubbleSalamander mob, PoseStack poses, float age, float yaw,
                                  float partialTick) {
        if (!mob.isWorn()) {
            super.applyRotations(mob, poses, age, yaw, partialTick);
            if (mob.isInWater()) {
                poses.translate(0, .7, 0);
                poses.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, mob.xRotO, mob.getXRot())));
                poses.translate(0, -.7, 0);
            }
        }
    }

    public static final class WornLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final BubbleSalamanderRenderer renderer;

        public WornLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                  EntityRendererProvider.Context context) {
            super(parent);
            renderer = new BubbleSalamanderRenderer(context);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
            var mob = BubbleSalamander.wornBy(player);
            if (mob == null) {
                return;
            }
            poses.pushPose();
            alignToHead(poses, getParentModel().head, getParentModel().body, player.getSwimAmount(partialTick));
            renderer.render(mob, 0, partialTick, poses, buffers, light);
            poses.popPose();
        }

        static void alignToHead(PoseStack poses, ModelPart head, ModelPart body, float swimAmount) {
            Quaternionf look = rotation(head);
            Vector3f center = look.transform(new Vector3f(0, -.25F, 0));
            poses.translate(head.x / 16.0 + center.x, head.y / 16.0 + center.y, head.z / 16.0 + center.z);
            // The salamander's tail points along +Z in its geometry, while the player's feet lie on +Y.
            Quaternionf swimming = rotation(body).rotateX(-Mth.HALF_PI);
            poses.mulPose(look.slerp(swimming, swimAmount));
            poses.scale(-1, -1, 1);
            poses.translate(0, -5 / 16.0, 5.1 / 16.0);
        }

        private static Quaternionf rotation(ModelPart part) {
            return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        }
    }

    private static final class Model extends GeoModel<BubbleSalamander> {
        @Override
        public ResourceLocation getModelResource(BubbleSalamander mob) {
            return id(mob.hasAirBladder() ? "geo/entity/bubble_salamander.geo.json" : "geo/entity/bubble_salamander_sheared.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(BubbleSalamander mob) {
            return BubbleSalamanderTextures.INSTANCE.texture(mob.color(), mob.mixedSeed());
        }

        @Override
        public void setCustomAnimations(BubbleSalamander mob, long instanceId, AnimationState<BubbleSalamander> state) {
            super.setCustomAnimations(mob, instanceId, state);
            boolean baby = mob.isBaby();
            getBone("female_frills").ifPresent(bone -> bone.setHidden(!mob.isFemale()));
            getBone("head").ifPresent(bone -> {
                float scale = baby ? 1.16F : 1;
                bone.setScaleX(scale);
                bone.setScaleY(scale);
                bone.setScaleZ(scale);
            });
            getBone("air_bladder").ifPresent(bone -> {
                bone.setScaleX(baby ? .82F : 1);
                bone.setScaleY(baby ? .78F : 1);
                bone.setScaleZ(baby ? .82F : 1);
            });
            getBone("tail_base").ifPresent(bone -> bone.setScaleZ(baby ? .73F : 1));
        }

        @Override
        public ResourceLocation getAnimationResource(BubbleSalamander mob) {
            return id("animations/entity/bubble_salamander.animation.json");
        }

        private static ResourceLocation id(String path) {
            return new ResourceLocation(ModIdentity.ID, path);
        }
    }
}

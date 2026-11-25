package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import yagen.waitmydawn.yagens_mobs.client.GeckoLivingRenderer;
import yagen.waitmydawn.yagens_mobs.client.GeoAppearance;
import yagen.waitmydawn.yagens_mobs.client.RenderPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.BoneSnapshots;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;

public final class BubbleSalamanderRenderer extends GeckoLivingRenderer<BubbleSalamander> {
    private static final DataTicket<Appearance> APPEARANCE = DataTicket.create("yagens_mobs:salamander_appearance", Appearance.class);

    public BubbleSalamanderRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
    }

    @Override
    public RenderType getRenderType(State state, Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRender(BubbleSalamander mob, Frustum frustum, double x, double y, double z, float tick) {
        return !mob.isWorn() && super.shouldRender(mob, frustum, x, y, z, tick);
    }

    @Override
    public boolean shouldShowName(BubbleSalamander mob, double distance) {
        return !mob.isWorn() && super.shouldShowName(mob, distance);
    }

    @Override
    public void addRenderData(BubbleSalamander mob, Void related, State state, float tick) {
        super.addRenderData(mob, related, state, tick);
        state.addGeckolibData(APPEARANCE, new Appearance(mob.getAgeScale(), mob.isBaby(), mob.isFemale(), mob.isWorn(),
                mob.isInWater() ? Mth.lerp(tick, mob.xRotO, mob.getXRot()) : 0));
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> pass, float width, float height) {
        float scale = pass.getGeckolibData(APPEARANCE).scale();
        super.scaleModelForRender(pass, width * scale, height * scale);
    }

    @Override
    protected float getShadowRadius(State state) {
        return state.isBaby ? .34F * .56F : .34F;
    }

    @Override
    protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        var appearance = pass.getGeckolibData(APPEARANCE);
        if (!appearance.worn()) {
            super.applyRotations(pass, poses, yaw);
            poses.translate(0, .7, 0);
            RenderPose.rotate(poses, Axis.XP.rotationDegrees(-appearance.pitch()));
            poses.translate(0, -.7, 0);
        }
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        var appearance = pass.getGeckolibData(APPEARANCE);
        bones.get("female_frills").ifPresent(bone -> bone.skipRender(!appearance.female()));
        if (appearance.baby()) {
            bones.get("head").ifPresent(bone -> bone.setScale(1.16F, 1.16F, 1.16F));
            bones.get("air_bladder").ifPresent(bone -> bone.setScale(.82F, .78F, .82F));
            bones.get("tail_base").ifPresent(bone -> bone.setScaleZ(.73F));
        }
    }

    private record Appearance(float scale, boolean baby, boolean female, boolean worn, float pitch) {}

    public static final class WornLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final BubbleSalamanderRenderer renderer;

        public WornLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
            super(parent);
            renderer = new BubbleSalamanderRenderer(context);
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState avatar,
                           float headYaw, float headPitch) {
            var game = Minecraft.getInstance();
            if (game.level == null || !(game.level.getEntity(avatar.id) instanceof Player player)) {
                return;
            }
            var mob = BubbleSalamander.wornBy(player);
            if (mob == null) {
                return;
            }
            float tick = game.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            var model = getParentModel();
            model.setupAnim(avatar);
            var state = renderer.createRenderState(mob, tick);
            poses.pushPose();
            alignToHead(poses, model.head, model.body, player.getSwimAmount(tick));
            renderer.performRenderPass(state, poses, collector,
                    game.gameRenderer.gameRenderState().levelRenderState.cameraRenderState);
            poses.popPose();
        }

        static void alignToHead(PoseStack poses, ModelPart head, ModelPart body, float swimAmount) {
            Quaternionf look = rotation(head);
            Vector3f center = look.transform(new Vector3f(0, -.25F, 0));
            poses.translate(head.x / 16.0 + center.x, head.y / 16.0 + center.y, head.z / 16.0 + center.z);
            // The salamander's tail points along +Z in its geometry, while the player's feet lie on +Y.
            Quaternionf swimming = rotation(body).rotateX(-Mth.HALF_PI);
            RenderPose.rotate(poses, look.slerp(swimming, swimAmount));
            poses.scale(-1, -1, 1);
            poses.translate(0, -5 / 16.0, 5.1 / 16.0);
        }

        private static Quaternionf rotation(ModelPart part) {
            return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        }
    }

    private static final class Model extends GeoAppearance<BubbleSalamander> {
        @Override
        public Identifier selectModel(BubbleSalamander mob) {
            return id("entity/bubble_salamander");
        }

        @Override
        public Identifier selectTexture(BubbleSalamander mob) {
            return BubbleSalamanderTextures.INSTANCE.texture(mob.color(), mob.mixedSeed());
        }

        @Override
        public Identifier getAnimationResource(BubbleSalamander mob) {
            return id("entity/bubble_salamander");
        }

        private static Identifier id(String path) {
            return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
        }
    }
}

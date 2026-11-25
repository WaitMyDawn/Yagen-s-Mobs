package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.geckolib.cache.animation.Animation;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.client.GeckoLivingRenderer;
import yagen.waitmydawn.yagens_mobs.client.GeoAppearance;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class KavatView extends GeckoLivingRenderer<Kavat> {
    private record Pose(float heading, boolean hidden, boolean owner, boolean fan, float rise, float sweep,
                        Map<String, KavatTurn.Joint> rig, KavatTurn.Frame turn) {}
    private static final String[][] FAN_BONES = new String[2][12];
    static {
        for (int hand = 0; hand < FAN_BONES.length; hand++)
            for (int joint = 0; joint < FAN_BONES[hand].length; joint++)
                FAN_BONES[hand][joint] = "inertia_" + (hand * 2 - 1) + "_" + (joint + 1);
    }
    private static final DataTicket<Pose> POSE = DataTicket.create("yagens_mobs:kavat_pose", Pose.class);
    public KavatView(EntityRendererProvider.Context context) { super(context, new Model()); }

    public static boolean localOwner(Kavat cat) {
        var viewer = Minecraft.getInstance().player;
        return viewer != null && cat.isOwnedBy(viewer);
    }

    @Override public void addRenderData(Kavat cat, Void related, State state, float tick) {
        super.addRenderData(cat, related, state, tick);
        var frame = new KavatTurn.Frame();
        frame.sample(cat.turnMotion, tick);
        state.addGeckolibData(POSE, new Pose(cat.turnMotion.heading(tick, cat.getYRot()), cat.hidden(), localOwner(cat),
                cat.hasFanTail(), cat.fanRise(tick), cat.fanSweep(tick),
                KavatGenes.TAILS.get(cat.genes().tail()).equals("leaf") ? KavatTurn.SHORT : KavatTurn.LONG, frame));
    }

    @Override protected float getShadowRadius(State state) { return state.hasGeckolibData(POSE) && state.getGeckolibData(POSE).hidden() ? 0 : .36F; }
    @Override protected void applyRotations(RenderPassInfo<State> pass, PoseStack poses, float yaw) {
        super.applyRotations(pass, poses, pass.getGeckolibData(POSE).heading());
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        var pose = pass.getGeckolibData(POSE);
        pose.rig().forEach((name, joint) -> bones.get(name).ifPresent(bone -> {
            bone.setRotX(bone.getRotX() + joint.pitch(pose.turn()));
            bone.setRotY(bone.getRotY() + joint.yaw(pose.turn()));
            bone.setTranslateY(bone.getTranslateY() + joint.height(pose.turn()));
        }));
        if (!pose.fan()) return;
        for (int hand = 0; hand < FAN_BONES.length; hand++) {
            int side = hand * 2 - 1;
            for (int joint = 0; joint < FAN_BONES[hand].length; joint++) {
                var pivot = bones.get(FAN_BONES[hand][joint]).orElse(null);
                if (pivot == null) continue;
                float weight = (joint + 1) / 12F;
                pivot.setRotZ(weight * (-side * .34F * pose.rise() + .22F * pose.sweep()));
                pivot.setRotY(weight * side * .20F * pose.sweep());
            }
        }
    }
    @Override public RenderType getRenderType(State state, Identifier tex) {
        var pose = state.getGeckolibData(POSE);
        return pose.hidden() ? pose.owner() ? RenderTypes.outline(tex) : null : RenderTypes.entityCutout(tex);
    }
    private static final class Model extends GeoAppearance<Kavat> {
        @Override protected Identifier selectModel(Kavat k) {
            return Identifier.fromNamespaceAndPath(ModIdentity.ID, "entity/kavat/" + k.genes().model());
        }
        @Override protected Identifier selectTexture(Kavat k) {
            return Identifier.fromNamespaceAndPath(ModIdentity.ID, "textures/entity/kavat/" + KavatGenes.COATS.get(k.genes().coat()) + ".png");
        }
        @Override public Identifier getAnimationResource(Kavat k){return KavatAnimations.LIBRARY;}
        @Override public Animation getBakedAnimation(Kavat k,String name){return KavatAnimations.INSTANCE.resolve(k.genes().model(),name);}
    }
}

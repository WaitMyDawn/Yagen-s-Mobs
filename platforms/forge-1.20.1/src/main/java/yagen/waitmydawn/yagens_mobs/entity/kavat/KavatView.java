package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class KavatView extends GeoEntityRenderer<Kavat> {
    private final KavatTurn.Frame turnFrame = new KavatTurn.Frame();
    private Map<String, KavatTurn.Joint> turnRig = KavatTurn.LONG;

    public KavatView(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .36F;
        withScale(1.5F);
    }
    public static boolean localOwner(Kavat cat) {
        var p = Minecraft.getInstance().player;
        return p != null && cat.isOwnedBy(p);
    }
    @Override
    public void render(Kavat cat, float yaw, float t, PoseStack poses, MultiBufferSource buffers, int light) {
        if (cat.hidden() && !localOwner(cat))
            return;
        turnFrame.sample(cat.turnMotion, t);
        turnRig = KavatGenes.TAILS.get(cat.genes().tail()).equals("leaf") ? KavatTurn.SHORT : KavatTurn.LONG;
        super.render(cat, yaw, t, poses, buffers, light);
    }
    @Override
    public RenderType getRenderType(Kavat cat, ResourceLocation tex, MultiBufferSource buffers, float t) {
        return cat.hidden() ? RenderType.outline(tex) : RenderType.entityCutoutNoCull(tex);
    }
    @Override
    protected void applyRotations(Kavat cat, PoseStack poses, float age, float yaw, float partial) {
        super.applyRotations(cat, poses, age, cat.turnMotion.heading(partial, yaw), partial);
    }

    @Override
    public void renderRecursively(PoseStack poses, Kavat cat, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer vertices, boolean reRender,
                                  float partial, int light, int overlay, float red, float green, float blue, float alpha) {
        var joint = turnRig.get(bone.getName());
        if (joint == null) {
            super.renderRecursively(poses, cat, bone, type, buffers, vertices, reRender, partial, light, overlay, red, green, blue, alpha);
            return;
        }
        float x = bone.getRotX(), y = bone.getRotY(), height = bone.getPosY();
        // Baked bones are shared. Restore the keyframed pose before another pass or entity can reuse it.
        try {
            bone.setRotX(x + joint.pitch(turnFrame));
            bone.setRotY(y + joint.yaw(turnFrame));
            bone.setPosY(height + joint.height(turnFrame));
            super.renderRecursively(poses, cat, bone, type, buffers, vertices, reRender, partial, light, overlay, red, green, blue, alpha);
        } finally {
            bone.setRotX(x);
            bone.setRotY(y);
            bone.setPosY(height);
        }
    }

    private static final class Model extends GeoModel<Kavat> {
        private static final String[][] FAN_SECTIONS=new String[2][12];
        static {
            for (int hand=0;hand<2;hand++)
                for (int j=0;j<12;j++)
                    FAN_SECTIONS[hand][j]="inertia_"+(hand*2-1)+"_"+(j+1);
        }
        @Override
        public void setCustomAnimations(Kavat cat,long instance,AnimationState<Kavat> state) {
            super.setCustomAnimations(cat,instance,state);
            if (!cat.hasFanTail()) return;
            float alpha=state.getPartialTick();
            float rise=cat.fanRise(alpha), sweep=cat.fanSweep(alpha);
            for (int hand=0;hand<2;hand++) {
                int side=hand*2-1;
                for (int j=0;j<12;j++) {
                    var bone=getBone(FAN_SECTIONS[hand][j]).orElse(null);
                    if (bone==null) continue;
                    float weight=(j+1)/12F;
                    // These neutral pivots carry only observed motion. Authored folds live on their children.
                    bone.setRotZ(weight*(-side*.34F*rise+.22F*sweep));
                    bone.setRotY(weight*side*.20F*sweep);
                }
            }
        }
        private static ResourceLocation id(String s) {
            return new ResourceLocation(ModIdentity.ID, s);
        }
        public ResourceLocation getModelResource(Kavat k) {
            return id("geo/entity/kavat/" + k.genes().model() + ".geo.json");
        }
        public ResourceLocation getAnimationResource(Kavat k) {
            return KavatAnimations.LIBRARY;
        }
        @Override
        public Animation getAnimation(Kavat cat, String name) {
            return KavatAnimations.INSTANCE.resolve(cat.genes().model(), name);
        }
        public ResourceLocation getTextureResource(Kavat k) {
            return id("textures/entity/kavat/" + KavatGenes.COATS.get(k.genes().coat()) + ".png");
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class KavatView extends GeoEntityRenderer<Kavat> {
    public KavatView(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .36F;
    }
    public static boolean localOwner(Kavat cat) {
        var p = Minecraft.getInstance().player;
        return p != null && cat.isOwnedBy(p);
    }
    @Override
    public void render(Kavat cat, float yaw, float t, PoseStack poses, MultiBufferSource buffers, int light) {
        if (cat.hidden() && !localOwner(cat))
            return;
        super.render(cat, yaw, t, poses, buffers, light);
    }
    @Override
    public RenderType getRenderType(Kavat cat, ResourceLocation tex, MultiBufferSource buffers, float t) {
        return cat.hidden() ? RenderType.outline(tex) : RenderType.entityCutoutNoCull(tex);
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
            return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, s);
        }
        public ResourceLocation getModelResource(Kavat k) {
            return id("geo/entity/kavat/" + k.genes().model() + ".geo.json");
        }
        public ResourceLocation getAnimationResource(Kavat k) {
            return id("animations/entity/kavat/" + k.genes().model() + ".animation.json");
        }
        public ResourceLocation getTextureResource(Kavat k) {
            return id("textures/entity/kavat/" + KavatGenes.COATS.get(k.genes().coat()) + ".png");
        }
    }
}

package yagen.waitmydawn.yagens_mobs.kavat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.KavatGenes;
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

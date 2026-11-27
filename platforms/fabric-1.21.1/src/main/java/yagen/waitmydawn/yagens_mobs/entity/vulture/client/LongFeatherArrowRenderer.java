package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.LongFeatherArrow;

public final class LongFeatherArrowRenderer extends ArrowRenderer<LongFeatherArrow> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ModIdentity.ID, "textures/entity/long_feather_arrow.png");

    public LongFeatherArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LongFeatherArrow arrow) {
        return TEXTURE;
    }
}

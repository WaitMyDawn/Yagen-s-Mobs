package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.vulture.LongFeatherArrow;

public final class LongFeatherArrowRenderer extends ArrowRenderer<LongFeatherArrow, ArrowRenderState> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            ModIdentity.ID, "textures/entity/long_feather_arrow.png");

    public LongFeatherArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }

    @Override
    public Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }
}

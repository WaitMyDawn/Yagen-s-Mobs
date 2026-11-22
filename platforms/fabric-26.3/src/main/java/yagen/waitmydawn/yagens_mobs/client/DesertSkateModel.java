package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.DesertSkate;

final class DesertSkateModel extends GeoAppearance<DesertSkate> {
    private static final Identifier ADULT = id("entity/desert_skate");
    private static final Identifier JUVENILE = id("entity/desert_skate_juvenile");
    private static final Identifier ANIMATIONS = id("entity/desert_skate");
    static final Identifier TEXTURE = id("textures/entity/desert_skate.png");

    @Override
    public Identifier selectModel(DesertSkate skate) {
        return skate.isBaby() ? JUVENILE : ADULT;
    }

    @Override
    public Identifier getAnimationResource(DesertSkate skate) {
        return ANIMATIONS;
    }

    @Override
    public Identifier selectTexture(DesertSkate skate) {
        return TEXTURE;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}

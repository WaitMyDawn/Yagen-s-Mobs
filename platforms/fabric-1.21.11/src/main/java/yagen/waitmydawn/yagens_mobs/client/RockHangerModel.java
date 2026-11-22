package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.RockHanger;

final class RockHangerModel extends GeoAppearance<RockHanger> {
    private static final Identifier MODEL = id("entity/rock_hanger");
    private static final Identifier ANIMATIONS = id("entity/rock_hanger");
    private static final Identifier TEXTURE = id("textures/entity/rock_hanger.png");

    @Override
    public Identifier selectModel(RockHanger mob) {
        return MODEL;
    }

    @Override
    public Identifier getAnimationResource(RockHanger mob) {
        return ANIMATIONS;
    }

    @Override
    public Identifier selectTexture(RockHanger mob) {
        return TEXTURE;
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}


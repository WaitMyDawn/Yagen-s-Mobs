package yagen.waitmydawn.yagens_mobs.entity.desertskate.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.DesertSkate;

final class DesertSkateModel extends GeoModel<DesertSkate> {
    private static final ResourceLocation ADULT = id("geo/entity/desert_skate.geo.json");
    private static final ResourceLocation JUVENILE = id("geo/entity/desert_skate_juvenile.geo.json");
    private static final ResourceLocation ANIMATIONS = id("animations/entity/desert_skate.animation.json");
    static final ResourceLocation TEXTURE = id("textures/entity/desert_skate.png");

    @Override
    public ResourceLocation getModelResource(DesertSkate skate) {
        return skate.isBaby() ? JUVENILE : ADULT;
    }

    @Override
    public ResourceLocation getAnimationResource(DesertSkate skate) {
        return ANIMATIONS;
    }

    @Override
    public ResourceLocation getTextureResource(DesertSkate skate) {
        return TEXTURE;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ModIdentity.ID, path);
    }
}

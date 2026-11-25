package yagen.waitmydawn.yagens_mobs.entity.rockhanger.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger;

final class RockHangerModel extends GeoModel<RockHanger> {
    private static final ResourceLocation MODEL = id("geo/entity/rock_hanger.geo.json");
    private static final ResourceLocation ANIMATIONS = id("animations/entity/rock_hanger.animation.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/rock_hanger.png");

    @Override
    public ResourceLocation getModelResource(RockHanger mob) {
        return MODEL;
    }

    @Override
    public ResourceLocation getAnimationResource(RockHanger mob) {
        return ANIMATIONS;
    }

    @Override
    public ResourceLocation getTextureResource(RockHanger mob) {
        return TEXTURE;
    }

    @Override
    public void setCustomAnimations(RockHanger mob, long instanceId, AnimationState<RockHanger> state) {
        super.setCustomAnimations(mob, instanceId, state);
        // Blockbench stages the descent on the body bone. In game the whole entity travels.
        getBone("body").ifPresent(bone -> bone.setPosY(0));
        getBone("tether").ifPresent(bone -> bone.setHidden(true));
        getBone("silk_tuft").ifPresent(bone -> bone.setHidden(mob.isTuftClipped()));
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}


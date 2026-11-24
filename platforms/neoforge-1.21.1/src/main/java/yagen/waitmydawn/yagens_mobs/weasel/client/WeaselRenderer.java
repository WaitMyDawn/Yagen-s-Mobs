package yagen.waitmydawn.yagens_mobs.weasel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.weasel.SnowcacheWeasel;
import yagen.waitmydawn.yagens_mobs.weasel.SnowcacheWeasels;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class WeaselRenderer extends GeoEntityRenderer<SnowcacheWeasel> {
    private WeaselRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .27F;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, SnowcacheWeasel entity) {
                return bone.getName().equals("item_socket") ? entity.mouthItem() : ItemStack.EMPTY;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, SnowcacheWeasel entity) {
                return ItemDisplayContext.GROUND;
            }

            @Override
            protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, SnowcacheWeasel entity,
                                              MultiBufferSource buffers, float tick, int light, int overlay) {
                pose.pushPose();
                pose.translate(0, -.012, .020);
                pose.scale(.58F, .58F, .58F);
                super.renderStackForBone(pose, bone, stack, entity, buffers, tick, light, overlay);
                pose.popPose();
            }
        });
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SnowcacheWeasels.TYPE.get(), WeaselRenderer::new);
    }

    @Override
    public RenderType getRenderType(SnowcacheWeasel weasel, ResourceLocation texture, MultiBufferSource buffers, float tick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    private static final class Model extends GeoModel<SnowcacheWeasel> {
        private static String name(SnowcacheWeasel weasel) {
            return "snowcache_weasel" + (weasel.isBaby() ? "_juvenile" : "");
        }

        @Override public ResourceLocation getModelResource(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("geo/entity/" + name(weasel) + ".geo.json");
        }

        @Override public ResourceLocation getTextureResource(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("textures/entity/" + name(weasel) + "_" + weasel.color() + ".png");
        }

        @Override public ResourceLocation getAnimationResource(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("animations/entity/" + name(weasel) + ".animation.json");
        }
    }
}


package yagen.waitmydawn.yagens_mobs.entity.collector.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import yagen.waitmydawn.yagens_mobs.entity.collector.Collector;

public final class CollectorRenderer extends GeoEntityRenderer<Collector> {
    public CollectorRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = .52F;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override protected ItemStack getStackForBone(GeoBone bone, Collector mob) {
                return switch (bone.getName()) {
                    case "sign_1" -> mob.sign(0);
                    case "sign_2" -> mob.sign(1);
                    case "sign_3" -> mob.sign(2);
                    case "cargo_path" -> mob.cargo();
                    default -> null;
                };
            }
            @Override protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack item, Collector mob,
                    MultiBufferSource buffers, float tick, int light, int overlay) {
                if (item.isEmpty()) return;
                boolean cargo = bone.getName().equals("cargo_path");
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(cargo ? 65 : 90));
                float scale = cargo ? .24F : .19F;
                pose.scale(scale,scale,cargo ? scale : .014F);
                Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.GUI, light, overlay,
                        pose,buffers,mob.level(),mob.getId());
                pose.popPose();
            }
        });
    }
    @Override public RenderType getRenderType(Collector mob, ResourceLocation texture, MultiBufferSource buffers, float tick) {
        return RenderType.entityCutoutNoCull(texture);
    }
    private static final class Model extends GeoModel<Collector> {
        @Override public ResourceLocation getModelResource(Collector mob) { return Collector.id("geo/entity/collector/"+mob.coat()+".geo.json"); }
        @Override public ResourceLocation getTextureResource(Collector mob) { return Collector.id("textures/entity/collector/"+mob.coat()+".png"); }
        @Override public ResourceLocation getAnimationResource(Collector mob) { return Collector.id("animations/entity/collector.animation.json"); }
        @Override public void setCustomAnimations(Collector mob,long instanceId,AnimationState<Collector> state) {
            super.setCustomAnimations(mob,instanceId,state);
            for (int i=0;i<3;i++) {
                boolean hidden=mob.sign(i).isEmpty();
                getBone("request_"+(i+1)).ifPresent(bone -> bone.setHidden(hidden));
            }
        }
    }
}


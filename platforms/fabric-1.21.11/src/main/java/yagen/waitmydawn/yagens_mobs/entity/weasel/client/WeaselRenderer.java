package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import yagen.waitmydawn.yagens_mobs.client.GeckoLivingRenderer;
import yagen.waitmydawn.yagens_mobs.client.GeoAppearance;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasel;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels;

public final class WeaselRenderer extends GeckoLivingRenderer<SnowcacheWeasel> {
    private static final DataTicket<ItemStack> MOUTH = DataTicket.create("yagens_mobs:weasel_mouth", ItemStack.class);
    private static final DataTicket<Float> SCALE = DataTicket.create("yagens_mobs:weasel_scale", Float.class);

    public WeaselRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            public void addRenderData(SnowcacheWeasel weasel, Void related, State state, float partialTick) {
                state.addGeckolibData(MOUTH, weasel.mouthItem());
            }

            @Override
            protected List<RenderData<State>> getRelevantBones(State state, BakedGeoModel model) {
                ItemStack item = state.getGeckolibData(MOUTH);
                return item.isEmpty() ? List.of() : List.of(new RenderData<>("item_socket", ItemDisplayContext.GROUND,
                        (bone, renderState) -> Either.left(item)));
            }

            @Override
            protected void submitItemStackRender(PoseStack pose, GeoBone bone, ItemStack item, ItemDisplayContext display,
                                                 State state, SubmitNodeCollector nodes, CameraRenderState camera,
                                                 int light, int overlay, int color) {
                pose.pushPose();
                pose.translate(0, -.012, .020);
                pose.scale(.58F, .58F, .58F);
                super.submitItemStackRender(pose, bone, item, display, state, nodes, camera, light, overlay, color);
                pose.popPose();
            }
        });
    }

    @Override
    public void addRenderData(SnowcacheWeasel weasel, Void related, State state, float partialTick) {
        super.addRenderData(weasel, related, state, partialTick);
        state.addGeckolibData(SCALE, weasel.getAgeScale());
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> pass, float width, float height) {
        float scale = pass.getGeckolibData(SCALE);
        super.scaleModelForRender(pass, width * scale, height * scale);
    }

    @Override
    protected float getShadowRadius(State state) {
        return .27F * state.getGeckolibData(SCALE);
    }

    private static final class Model extends GeoAppearance<SnowcacheWeasel> {
        private static String name(SnowcacheWeasel weasel) {
            return "snowcache_weasel" + (weasel.isBaby() ? "_juvenile" : "");
        }

        @Override protected Identifier selectModel(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("entity/" + name(weasel));
        }

        @Override protected Identifier selectTexture(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("textures/entity/" + name(weasel) + "_" + weasel.color() + ".png");
        }

        @Override public Identifier getAnimationResource(SnowcacheWeasel weasel) {
            return SnowcacheWeasels.id("entity/" + name(weasel));
        }
    }
}

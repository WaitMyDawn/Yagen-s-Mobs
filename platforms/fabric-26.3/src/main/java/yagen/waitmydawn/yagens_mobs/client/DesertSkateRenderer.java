package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.entity.DesertSkate;

final class DesertSkateRenderer extends GeckoLivingRenderer<DesertSkate> {
    private static final DataTicket<Float> AGE_SCALE = DataTicket.create("yagens_mobs:skate_age_scale", Float.class);

    DesertSkateRenderer(EntityRendererProvider.Context context) {
        super(context, new DesertSkateModel());
    }

    @Override
    public boolean shouldRender(DesertSkate skate, Frustum frustum, double x, double y, double z, float tick) {
        return !skate.isBurrowed() && super.shouldRender(skate, frustum, x, y, z, tick);
    }

    @Override
    public void addRenderData(DesertSkate skate, Void related, State state, float tick) {
        super.addRenderData(skate, related, state, tick);
        state.addGeckolibData(AGE_SCALE, skate.getAgeScale());
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> pass, float scaleWidth, float scaleHeight) {
        var scale = pass.getOrDefaultGeckolibData(AGE_SCALE, 1F);
        super.scaleModelForRender(pass, scaleWidth * scale, scaleHeight * scale);
    }

    @Override
    protected float getShadowRadius(State state) {
        return .45F * state.getOrDefaultGeckolibData(AGE_SCALE, 1F);
    }
}

package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.resources.Identifier;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

public abstract class GeoAppearance<T extends GeoAnimatable> extends GeoModel<T> {
    private static final DataTicket<Identifier> MODEL = DataTicket.create("yagen.waitmydawn.yagens_mobs:model", Identifier.class);
    private static final DataTicket<Identifier> TEXTURE = DataTicket.create("yagen.waitmydawn.yagens_mobs:texture", Identifier.class);

    protected abstract Identifier selectModel(T mob);

    protected abstract Identifier selectTexture(T mob);

    @Override
    public void addAdditionalStateData(T mob, Object relatedObject, GeoRenderState state) {
        state.addGeckolibData(MODEL, selectModel(mob));
        state.addGeckolibData(TEXTURE, selectTexture(mob));
    }

    @Override
    public final Identifier getModelResource(GeoRenderState state) {
        return state.getGeckolibData(MODEL);
    }

    @Override
    public final Identifier getTextureResource(GeoRenderState state) {
        return state.getGeckolibData(TEXTURE);
    }
}

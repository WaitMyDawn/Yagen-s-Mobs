package yagen.waitmydawn.yagens_mobs.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

abstract class GeckoLivingRenderer<T extends LivingEntity & GeoAnimatable>
        extends GeoEntityRenderer<T, GeckoLivingRenderer.State> {
    protected GeckoLivingRenderer(EntityRendererProvider.Context context, GeoAppearance<T> model) {
        super(context, model);
    }

    @Override
    public State createRenderState(T entity, Void context) {
        return new State();
    }

    static final class State extends LivingEntityRenderState implements GeoRenderState {
        private final Map<DataTicket<?>, Object> data = new HashMap<>();

        @Override
        public <D> void addGeckolibData(DataTicket<D> ticket, D value) {
            data.put(ticket, value);
        }

        @Override
        public boolean hasGeckolibData(DataTicket<?> ticket) {
            return data.containsKey(ticket);
        }

        @Override
        public Map<DataTicket<?>, Object> getDataMap() {
            return data;
        }
    }
}

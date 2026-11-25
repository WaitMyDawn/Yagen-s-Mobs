package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class IncubatorRenderer extends GeoBlockRenderer<Incubator, IncubatorRenderer.State> {
    public static final class State extends BlockEntityRenderState implements GeoRenderState {
        private final Map<DataTicket<?>, Object> data = new HashMap<>();
        private boolean packed;

        // GeckoLib injects concrete accessors into BlockEntityRenderState. All access must share this map.
        @Override public <D> void addGeckolibData(DataTicket<D> ticket, D value) { data.put(ticket, value); }
        @Override public boolean hasGeckolibData(DataTicket<?> ticket) { return data.containsKey(ticket); }
        @Override public Map<DataTicket<?>, Object> getDataMap() { return data; }
    }

    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "kavat_incubator")));
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(Incubator chamber, State state, float tick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        chamber.sampleShutters(tick);
        super.extractRenderState(chamber, state, tick, camera, overlay);
        state.packed = chamber.isPacked();
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
        poses.pushPose();
        if (state.packed) poses.scale(.5f, .5f, .5f);
        poses.translate(.5, 0, .5);
        super.submit(state, poses, nodes, camera);
        poses.popPose();
    }
}

package yagen.waitmydawn.yagens_mobs.salvage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import yagen.waitmydawn.yagens_mobs.salvage.client.SalvageRayRenderer;

public final class SalvageClient {
    private SalvageClient() {}

    public static void register() {
        SalvageRayRenderer.register();
        SalvageNestScreen.register();
        ClientTickEvents.END_CLIENT_TICK.register(SalvageLinkOverlay::tick);
        WorldRenderEvents.LAST.register(SalvageLinkOverlay::render);
        ClientPlayNetworking.registerGlobalReceiver(SalvageRoutes.Snapshot.TYPE, (snapshot, context) -> {
            switch (snapshot.data().getString("Mode")) {
                case "Overlay" -> SalvageLinkOverlay.accept(snapshot.data());
                case "Rename" -> SalvageNestScreen.renamed(snapshot.data());
                default -> SalvageRouteScreen.accept(snapshot);
            }
        });
    }

    public static boolean cutPointedLink() {
        return SalvageLinkOverlay.use();
    }
}

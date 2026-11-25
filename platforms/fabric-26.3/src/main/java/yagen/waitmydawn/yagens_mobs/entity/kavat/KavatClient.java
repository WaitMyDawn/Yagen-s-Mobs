package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class KavatClient {
    public static void register() {
        HudElementRegistry.addLast(KavatRescue.ID, KavatRescueView::draw);
        ClientPlayNetworking.registerGlobalReceiver(KavatRescue.TYPE, (packet, context) -> KavatRescueView.flash());
        ClientPlayNetworking.registerGlobalReceiver(IncubatorPage.TYPE, (packet, context) -> {
            if (context.player().containerMenu instanceof IncubatorMenu menu && menu.containerId == packet.menu())
                menu.receive(packet.view());
        });
    }
}

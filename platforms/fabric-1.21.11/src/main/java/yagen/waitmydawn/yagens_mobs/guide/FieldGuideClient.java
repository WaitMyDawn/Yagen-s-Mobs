package yagen.waitmydawn.yagens_mobs.guide;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import yagen.waitmydawn.yagens_mobs.guide.client.GuidePlates;
import yagen.waitmydawn.yagens_mobs.guide.client.PatchouliReader;

public final class FieldGuideClient {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FieldGuide.Open.TYPE, (packet, context) -> PatchouliReader.open(packet.known()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GuidePlates.clear());
    }
    private FieldGuideClient() {}
}

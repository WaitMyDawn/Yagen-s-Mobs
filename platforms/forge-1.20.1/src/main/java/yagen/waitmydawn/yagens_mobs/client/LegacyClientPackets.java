package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.Minecraft;
import yagen.waitmydawn.yagens_mobs.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;

public final class LegacyClientPackets {
    private LegacyClientPackets() {}

    public static void updateStation(StationPackets.Snapshot snapshot) {
        if (snapshot.data() != null) {
            VultureStationScreen.accept(snapshot);
        }
    }

    public static void updateMailName(StationPackets.MailName label) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MailBagMenu menu && menu.containerId == label.containerId()) {
            menu.updateBoundLabel(label.name());
        }
    }
}

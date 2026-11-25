package yagen.waitmydawn.yagens_mobs.client;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;

import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VultureStationScreen;

import net.minecraft.client.Minecraft;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationPackets;

public final class LegacyClientPackets {
    private LegacyClientPackets() {}

    public static void incubatorPage(IncubatorPage msg) {
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null && player.containerMenu instanceof IncubatorMenu menu && menu.containerId==msg.menu()) menu.receive(msg.view());
    }
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

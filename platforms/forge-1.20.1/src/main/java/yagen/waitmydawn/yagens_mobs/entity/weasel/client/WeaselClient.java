package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;

public final class WeaselClient {
    private WeaselClient() {}

    static void request(CompoundTag tag) {
        yagen.waitmydawn.yagens_mobs.network.LegacyNetwork.sendToServer(
                new yagen.waitmydawn.yagens_mobs.entity.weasel.WeaselPackets.Request(tag));
    }

    public static void open(CompoundTag payload) {
        if (WorkPreviewClient.accept(Minecraft.getInstance(), payload)) return;
        if (payload != null && payload.getBoolean("Storage")) {
            WeaselPortScreen.open(payload);
            return;
        }
        if (ModList.get().isLoaded("jei") && WeaselJei.runtime() != null && payload.hasUUID("Session")) {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new BlueprintScreen(payload.getUUID("Session"),
                    yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintPlan.load(payload.getCompound("Plan"))));
        }
    }
}


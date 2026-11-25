package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels;
import yagen.waitmydawn.yagens_mobs.entity.weasel.WeaselPackets;

public final class WeaselClient {
    private WeaselClient() {}

    static void request(CompoundTag tag) {
        ClientPlayNetworking.send(new WeaselPackets.Request(tag));
    }

    public static void register() {
        WeaselPortScreen.register();
        EntityRendererRegistry.register(SnowcacheWeasels.TYPE.get(), WeaselRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(WeaselPackets.Opening.TYPE, (payload, context) -> open(payload.tag()));
    }

    private static void open(CompoundTag payload) {
        if (payload != null && payload.getBoolean("Storage")) {
            WeaselPortScreen.open(payload);
            return;
        }
        if (FabricLoader.getInstance().isModLoaded("jei") && WeaselJei.runtime() != null && payload.hasUUID("Session")) {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new BlueprintScreen(payload.getUUID("Session"),
                    yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintPlan.load(payload.getCompound("Plan"))));
        }
    }
}


package yagen.waitmydawn.yagens_mobs.weasel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import yagen.waitmydawn.yagens_mobs.weasel.SnowcacheWeasels;
import yagen.waitmydawn.yagens_mobs.weasel.WeaselPackets;
import yagen.waitmydawn.yagens_mobs.client.WeaselRenderer;

public final class WeaselClient {
    private WeaselClient() {}

    static void request(CompoundTag tag) {
        ClientPlayNetworking.send(new WeaselPackets.Request(tag));
    }

    public static void register() {
        EntityRendererRegistry.register(SnowcacheWeasels.TYPE.get(), WeaselRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(WeaselPackets.Opening.TYPE, (payload, context) -> open(payload.tag()));
    }

    private static void open(CompoundTag payload) {
        if (FabricLoader.getInstance().isModLoaded("jei") && WeaselJei.runtime() != null) {
            var session = yagen.waitmydawn.yagens_mobs.compat.SaveTag.id(payload, "Session").orElse(null);
            if (session == null) return;
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new BlueprintScreen(session,
                    yagen.waitmydawn.yagens_mobs.weasel.BlueprintPlan.load(payload.getCompoundOrEmpty("Plan"))));
        }
    }
}


package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
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
        ClientTickEvents.END_CLIENT_TICK.register(game -> WorkPreviewClient.tick(game, WeaselClient::request));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(WeaselWorkOverlay::render);
        EntityRendererRegistry.register(SnowcacheWeasels.TYPE.get(), WeaselRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(WeaselPackets.Opening.TYPE, (payload, context) -> open(payload.tag()));
    }

    private static void open(CompoundTag payload) {
        if (WorkPreviewClient.accept(Minecraft.getInstance(), payload)) return;
        if (payload != null && payload.getBooleanOr("Storage", false)) {
            WeaselPortScreen.open(payload);
            return;
        }
        if (FabricLoader.getInstance().isModLoaded("jei") && WeaselJei.runtime() != null) {
            var session = yagen.waitmydawn.yagens_mobs.compat.SaveTag.id(payload, "Session").orElse(null);
            if (session == null) return;
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new BlueprintScreen(session,
                    yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintPlan.load(payload.getCompoundOrEmpty("Plan"))));
        }
    }
}


package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamanderWear;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class BubbleSalamanderClient {
    private static final KeyMapping REMOVE = new KeyMapping("key.yagens_mobs.remove_bubble_salamander",
            GLFW.GLFW_KEY_B, "itemGroup.yagens_mobs");

    private BubbleSalamanderClient() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(REMOVE);
    }

    @SubscribeEvent
    public static void releaseOnKey(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BubbleSalamanderTextures.INSTANCE.tick();
        var client = Minecraft.getInstance();
        var player = client.player;
        if (REMOVE.consumeClick() && client.screen == null && player != null && BubbleSalamander.wornBy(player) != null) {
            LegacyNetwork.sendToServer(BubbleSalamanderWear.Remove.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void waterFog(ViewportEvent.RenderFog event) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player != null && event.getCamera().getEntity() == player && client.options.getCameraType().isFirstPerson()
                && event.getType() == FogType.WATER && BubbleSalamander.isProvidingAir(player)) {
            event.scaleFarPlaneDistance(1.7F);
            event.setCanceled(true);
        }
    }
}

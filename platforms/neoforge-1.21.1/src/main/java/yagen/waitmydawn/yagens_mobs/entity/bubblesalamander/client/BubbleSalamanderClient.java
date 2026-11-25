package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import yagen.waitmydawn.yagens_mobs.client.PetCommandControls;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderWear;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class BubbleSalamanderClient {
    private static final KeyMapping REMOVE = new KeyMapping("key.yagens_mobs.remove_bubble_salamander",
            GLFW.GLFW_KEY_B, "itemGroup.yagens_mobs");

    private static final KeyMapping DISENGAGE = new KeyMapping("key.yagens_mobs.disengage_pets",
            com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), REMOVE.getCategory());

    private BubbleSalamanderClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(REMOVE);
        event.register(DISENGAGE);
    }

    @SubscribeEvent
    public static void releaseOnKey(ClientTickEvent.Post event) {
        BubbleSalamanderTextures.INSTANCE.tick();
        var client = Minecraft.getInstance();
        var player = client.player;
        if (DISENGAGE.consumeClick()) PetCommandControls.disengage(client);
        if (REMOVE.consumeClick() && client.screen == null && player != null && BubbleSalamander.wornBy(player) != null) {
            PacketDistributor.sendToServer(BubbleSalamanderWear.Remove.INSTANCE);
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

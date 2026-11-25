package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderWear;

public final class BubbleSalamanderClient {
    private static final KeyMapping REMOVE = new KeyMapping("key.yagens_mobs.remove_bubble_salamander",
            GLFW.GLFW_KEY_B, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("yagens_mobs", "controls")));

    private BubbleSalamanderClient() {
    }

    public static void registerKeys() {
        KeyBindingHelper.registerKeyBinding(REMOVE);
    }

    public static void releaseOnKey(Minecraft ignored) {
        BubbleSalamanderTextures.INSTANCE.tick();
        var client = Minecraft.getInstance();
        var player = client.player;
        if (REMOVE.consumeClick() && client.screen == null && player != null && BubbleSalamander.wornBy(player) != null) {
            ClientPlayNetworking.send(BubbleSalamanderWear.Remove.INSTANCE);
        }
    }

}

package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import yagen.waitmydawn.yagens_mobs.client.PetCommandControls;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderWear;

public final class BubbleSalamanderClient {
    private static final KeyMapping REMOVE = new KeyMapping("key.yagens_mobs.remove_bubble_salamander",
            com.mojang.blaze3d.platform.InputConstants.KEY_B, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("yagens_mobs", "controls")));

    private static final KeyMapping DISENGAGE = new KeyMapping("key.yagens_mobs.disengage_pets",
            com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), REMOVE.getCategory());

    private BubbleSalamanderClient() {
    }

    public static void registerKeys() {
        KeyMappingHelper.registerKeyMapping(REMOVE);
        KeyMappingHelper.registerKeyMapping(DISENGAGE);
    }

    public static void releaseOnKey(Minecraft ignored) {
        BubbleSalamanderTextures.INSTANCE.tick();
        var client = Minecraft.getInstance();
        var player = client.player;
        if (DISENGAGE.consumeClick()) PetCommandControls.disengage(client);
        if (REMOVE.consumeClick() && client.gui.screen() == null && player != null && BubbleSalamander.wornBy(player) != null) {
            ClientPlayNetworking.send(BubbleSalamanderWear.Remove.INSTANCE);
        }
    }

}

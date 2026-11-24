package yagen.waitmydawn.yagens_mobs.weasel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

final class WeaselPlatform {
    private WeaselPlatform() {}

    static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(WeaselPackets.Request.TYPE, WeaselPackets.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeaselPackets.Opening.TYPE, WeaselPackets.Opening.CODEC);
    }

    static ItemStack assemble(CraftingRecipe recipe, CraftingInput input, ServerLevel level) {
        return recipe.assemble(input);
    }

    static void sendStatus(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}

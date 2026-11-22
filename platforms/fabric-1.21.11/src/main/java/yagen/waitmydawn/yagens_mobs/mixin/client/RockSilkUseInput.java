package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.client.RockSilkControls;

@Mixin(Minecraft.class)
abstract class RockSilkUseInput {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void yagensMobs$tryRockSilkGrip(CallbackInfo callback) {
        var game = (Minecraft) (Object) this;
        if (game.gameMode != null && game.player != null && !game.player.isUsingItem()
                && RockSilkControls.tryGrab()) {
            callback.cancel();
        }
    }
}

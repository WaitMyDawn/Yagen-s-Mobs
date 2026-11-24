package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.yagens_mobs.weasel.WeaselPortPackets;

@Mixin(ServerPlayer.class)
abstract class WeaselNativeMenu {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void yagensMobs$nativeStorageMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) WeaselPortPackets.onOpen((ServerPlayer) (Object) this);
    }
}

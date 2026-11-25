package yagen.waitmydawn.yagens_mobs.entity.salvage.mixin;

import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.salvage.SalvagePlayerMotion;

@Mixin(ServerPlayer.class)
abstract class SalvageClientViewDistance implements SalvagePlayerMotion.ViewDistance {
    @Unique private int yagensMobs$viewDistance = 2;

    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void yagensMobs$readViewDistance(ServerboundClientInformationPacket packet, CallbackInfo callback) {
        yagensMobs$viewDistance = Mth.clamp(packet.viewDistance(), 2, 32);
    }

    @Override
    public int yagensMobs$viewDistance() {
        return yagensMobs$viewDistance;
    }
}

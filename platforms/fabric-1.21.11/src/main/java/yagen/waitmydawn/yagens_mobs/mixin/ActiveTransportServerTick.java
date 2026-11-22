package yagen.waitmydawn.yagens_mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.transport.StationData;

@Mixin(MinecraftServer.class)
abstract class ActiveTransportServerTick {
    @ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;pauseWhenEmptySeconds()I", ordinal = 0))
    private int yagensMobs$keepActiveFlightsRunning(int pauseSeconds) {
        if (pauseSeconds > 0) {
            var server = (MinecraftServer) (Object) this;
            for (var level : server.getAllLevels()) {
                if (StationData.get(level).hasActiveFlights()) {
                    // Chunk tickets keep the dimension active, while this check controls the dedicated server's idle pause.
                    return 0;
                }
            }
        }
        return pauseSeconds;
    }
}

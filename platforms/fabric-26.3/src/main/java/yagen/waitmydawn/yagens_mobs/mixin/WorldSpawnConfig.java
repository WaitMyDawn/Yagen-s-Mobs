package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

@Mixin(value = MinecraftServer.class, priority = 1500)
abstract class WorldSpawnConfig {
    @Inject(method = "<init>", at = @At("RETURN"), order = 900)
    private void yagensMobs$readWorldConfig(CallbackInfo callback) {
        // Fabric applies biome modifications at constructor return, before SERVER_STARTING.
        ServerConfigs.reload((MinecraftServer) (Object) this);
    }
}

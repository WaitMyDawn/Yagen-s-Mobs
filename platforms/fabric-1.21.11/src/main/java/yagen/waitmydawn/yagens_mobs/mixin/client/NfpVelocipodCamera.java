package yagen.waitmydawn.yagens_mobs.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.client.VelocipodCamera;

@Pseudo
@Mixin(targets = "yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera", remap = false)
abstract class NfpVelocipodCamera {
    @Shadow
    private static boolean available() {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
    private static void yagensMobs$registerView(CallbackInfo callback) {
        VelocipodCamera.registerNfpShoulderView(NfpVelocipodCamera::available);
    }
}

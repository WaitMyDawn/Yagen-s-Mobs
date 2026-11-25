package yagen.waitmydawn.yagens_mobs.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodCamera;

@Pseudo
@Mixin(targets = "yagen.waitmydawn.ninjas_free_play.camera.core.CameraRig", remap = false)
abstract class NfpVelocipodCameraRig {
    @ModifyArg(method = "sample", at = @At(value = "INVOKE",
            target = "Lyagen/waitmydawn/ninjas_free_play/camera/core/CameraGeometry;offset("
                    + "Lyagen/waitmydawn/ninjas_free_play/camera/core/CameraGeometry$Basis;DDD)"
                    + "Lyagen/waitmydawn/ninjas_free_play/animation/core/Vector3;"),
            index = 1, require = 0)
    private double yagensMobs$extendCollisionSweep(double distance) {
        // Apply speed after NFP's view transition and before its collision sweep.
        return distance + VelocipodCamera.nfpScaledExtraDistance();
    }
}

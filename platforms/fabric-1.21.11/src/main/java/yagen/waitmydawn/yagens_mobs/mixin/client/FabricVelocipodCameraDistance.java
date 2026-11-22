package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import yagen.waitmydawn.yagens_mobs.client.VelocipodCamera;

@Mixin(Camera.class)
abstract class FabricVelocipodCameraDistance {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float yagensMobs$extendRearCamera(float distance) {
        return VelocipodCamera.addRearDistance((Camera) (Object) this, distance);
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodCamera;

@Mixin(Camera.class)
public abstract class VelocipodRearCamera {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
    private double yagensMobs$extendRearCamera(double distance) {
        return distance + VelocipodCamera.vanillaExtraDistance();
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;

@Mixin(PlayerRenderer.class)
abstract class PlayerVelocipodOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$standAcrossVelocipod(AbstractClientPlayer player, PoseStack poses, float age, float yaw,
                                               float partialTick, float scale, CallbackInfo callback) {
        if (player.getVehicle() instanceof Velocipod pod && player.isAlive()) {
            float facing = Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot());
            poses.mulPose(Axis.YP.rotationDegrees(180 - facing));
            poses.mulPose(pod.riderTilt(partialTick));
            poses.mulPose(Axis.YP.rotationDegrees(-52));
            callback.cancel();
        }
    }
}


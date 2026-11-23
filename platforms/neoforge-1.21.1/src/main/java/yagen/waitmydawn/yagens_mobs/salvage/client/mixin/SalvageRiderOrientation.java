package yagen.waitmydawn.yagens_mobs.salvage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRay;

@Mixin(PlayerRenderer.class)
abstract class SalvageRiderOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$faceWithRay(AbstractClientPlayer player, PoseStack poses, float age, float yaw,
                                       float partial, float scale, CallbackInfo callback) {
        if (player.getVehicle() instanceof SalvageRay ray && player.isAlive()) {
            poses.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, ray.yRotO, ray.getYRot())));
            poses.translate(0, SalvageRay.RIDER_DROP, 0);
            poses.mulPose(Axis.XP.rotationDegrees(ray.flightPitch(partial) * .76F));
            poses.mulPose(Axis.ZP.rotationDegrees(ray.flightBank(partial) * .9F));
            poses.translate(0, -SalvageRay.RIDER_DROP, 0);
            callback.cancel();
        }
    }
}

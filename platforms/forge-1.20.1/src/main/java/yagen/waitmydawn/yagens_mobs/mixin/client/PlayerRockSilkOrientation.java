package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkGrip;

@Mixin(PlayerRenderer.class)
abstract class PlayerRockSilkOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$faceRockSilk(AbstractClientPlayer player, PoseStack stack, float age, float bodyYaw,
                                        float partialTick, CallbackInfo callback) {
        if (player.getVehicle() instanceof RockSilkGrip grip) {
            // Tilt about the attachment height so the hands stay at the same point on the line.
            double pivot = RockSilkGrip.holdHeight(player, grip.hangAmount());
            stack.translate(0, pivot, 0);
            var up = grip.modelUp();
            stack.mulPose(new Quaternionf().rotationTo(0, 1, 0, up.x, up.y, up.z));
            stack.translate(0, -pivot, 0);
            stack.mulPose(Axis.YP.rotationDegrees(180 - grip.getYRot()));
            callback.cancel();
        }
    }
}


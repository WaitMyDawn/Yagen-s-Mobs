package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkGrip;

@Mixin(AvatarRenderer.class)
abstract class PlayerRockSilkOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$faceRockSilk(AvatarRenderState state, PoseStack stack, float yaw, float scale, CallbackInfo callback) {
        var game = Minecraft.getInstance();
        if (game.level == null || !(game.level.getEntity(state.id) instanceof Player player)) return;
        float partialTick = game.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (player.getVehicle() instanceof RockSilkGrip grip) {
            // Tilt about the attachment height so the hands stay at the same point on the line.
            double pivot = RockSilkGrip.holdHeight(player, grip.hangAmount()) / scale;
            stack.translate(0, pivot, 0);
            var up = grip.modelUp();
            yagen.waitmydawn.yagens_mobs.client.RenderPose.rotate(stack, new Quaternionf().rotationTo(0, 1, 0, up.x, up.y, up.z));
            stack.translate(0, -pivot, 0);
            yagen.waitmydawn.yagens_mobs.client.RenderPose.rotate(stack, Axis.YP.rotationDegrees(180 - grip.getYRot()));
            callback.cancel();
        }
    }
}


package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

@Mixin(AvatarRenderer.class)
abstract class PlayerVelocipodOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$standAcrossVelocipod(AvatarRenderState state, PoseStack poses, float yaw, float scale, CallbackInfo callback) {
        var game = Minecraft.getInstance();
        if (game.level == null || !(game.level.getEntity(state.id) instanceof Player player)) return;
        float partialTick = game.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (player.getVehicle() instanceof Velocipod pod && player.isAlive()) {
            float facing = Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot());
            poses.mulPose(Axis.YP.rotationDegrees(180 - facing));
            poses.mulPose(pod.riderTilt(partialTick));
            poses.mulPose(Axis.YP.rotationDegrees(-52));
            callback.cancel();
        }
    }
}


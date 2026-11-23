package yagen.waitmydawn.yagens_mobs.salvage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.Mth;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRay;

@Mixin(AvatarRenderer.class)
abstract class SalvageRiderOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$faceWithRay(AvatarRenderState state, PoseStack stack, float yaw, float scale, CallbackInfo callback) {
        var game = Minecraft.getInstance();
        if (game.level == null || !(game.level.getEntity(state.id) instanceof Player player)) return;
        float partialTick = game.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (player.getVehicle() instanceof SalvageRay ray && player.isAlive()) {
            yagen.waitmydawn.yagens_mobs.client.RenderPose.rotate(stack, Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, ray.yRotO, ray.getYRot())));
            double pivot = SalvageRay.RIDER_DROP / scale;
            stack.translate(0, pivot, 0);
            yagen.waitmydawn.yagens_mobs.client.RenderPose.rotate(stack, Axis.XP.rotationDegrees(ray.flightPitch(partialTick) * .76F));
            yagen.waitmydawn.yagens_mobs.client.RenderPose.rotate(stack, Axis.ZP.rotationDegrees(ray.flightBank(partialTick) * .9F));
            stack.translate(0, -pivot, 0);
            callback.cancel();
        }
    }
}

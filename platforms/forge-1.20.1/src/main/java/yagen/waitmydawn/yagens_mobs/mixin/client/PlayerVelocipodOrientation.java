package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.RiderMountMotion;

@Mixin(PlayerRenderer.class)
abstract class PlayerVelocipodOrientation {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$standAcrossVelocipod(AbstractClientPlayer player, PoseStack poses, float age, float yaw,
                                               float partialTick, CallbackInfo callback) {
        var motion = RiderMountMotion.sample(player, partialTick);
        if (motion == null) return;
        float weight = motion.weight(player.tickCount + (double) partialTick);
        if (weight <= 0) return;
        // The brief lift clears the leading foot while the hips turn across the back.
        poses.translate(0, .11F * Mth.sin(Mth.PI * weight), 0);
        poses.mulPose(motion.rotation(yaw, weight));
        callback.cancel();
    }
}

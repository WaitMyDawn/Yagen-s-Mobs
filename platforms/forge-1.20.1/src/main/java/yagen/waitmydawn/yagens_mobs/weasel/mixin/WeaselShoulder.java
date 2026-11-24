package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.weasel.SnowcacheWeasel;

@Mixin(Entity.class)
abstract class WeaselShoulder {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void yagensMobs$placeWeaselOnShoulder(Entity passenger, Entity.MoveFunction move, CallbackInfo callback) {
        if ((Object) this instanceof Player player && passenger instanceof SnowcacheWeasel weasel
                && passenger.getVehicle() == player) {
            float yaw = weasel.isBaby() ? player.yBodyRot : player.getYHeadRot();
            double angle = yaw * Mth.DEG_TO_RAD;
            if (weasel.isBaby()) {
                double y = player.getEyeY() - (player.isSwimming() ? .08 : .23);
                move.accept(weasel, player.getX() + Math.cos(angle) * .36, y,
                        player.getZ() + Math.sin(angle) * .36);
            } else {
                double seatHeight = player.isCrouching() ? .40 : .30;
                if (player.isVisuallySwimming()) seatHeight = .34;
                double forward = player.isCrouching() ? .13 : 0;
                move.accept(weasel, player.getX() - Math.sin(angle) * forward,
                        player.getEyeY() + seatHeight, player.getZ() + Math.cos(angle) * forward);
            }
            weasel.setYRot(yaw);
            weasel.setYBodyRot(yaw);
            weasel.setYHeadRot(yaw);
            callback.cancel();
        }
    }
}


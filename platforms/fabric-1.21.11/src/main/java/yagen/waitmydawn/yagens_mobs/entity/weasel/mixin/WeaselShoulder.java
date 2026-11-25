package yagen.waitmydawn.yagens_mobs.entity.weasel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasel;

@Mixin(Entity.class)
abstract class WeaselShoulder {
    @ModifyExpressionValue(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean yagensMobs$allowOwnedPerch(boolean serializable, @Local(argsOnly = true) Entity vehicle) {
        // Modern vanilla rejects non-serializable vehicles, including players, before checking forced mounts.
        return serializable || (Object) this instanceof SnowcacheWeasel weasel
                && vehicle instanceof Player player && weasel.isTame() && weasel.isOwnedBy(player);
    }


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


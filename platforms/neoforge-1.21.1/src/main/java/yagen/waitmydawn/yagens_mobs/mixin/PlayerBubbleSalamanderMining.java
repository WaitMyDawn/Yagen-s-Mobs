package yagen.waitmydawn.yagens_mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamander;

@Mixin(Player.class)
abstract class PlayerBubbleSalamanderMining {
    @ModifyExpressionValue(method = "getDigSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean yagensMobs$ignoreSubmergedPenalty(boolean submerged) {
        return submerged && !BubbleSalamander.isProvidingAir((Player) (Object) this);
    }

    @ModifyExpressionValue(method = "getDigSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean yagensMobs$ignoreFloatingPenalty(boolean grounded) {
        return grounded || BubbleSalamander.isProvidingAir((Player) (Object) this);
    }
}

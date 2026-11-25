package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;

@Mixin(LivingEntity.class)
abstract class BubbleSalamanderFatigue {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void yagensMobs$ignoreMiningFatigue(MobEffectInstance effect, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayer player && effect.getEffect().equals(MobEffects.MINING_FATIGUE)
                && BubbleSalamander.isProvidingAir(player)) callback.setReturnValue(false);
    }
}

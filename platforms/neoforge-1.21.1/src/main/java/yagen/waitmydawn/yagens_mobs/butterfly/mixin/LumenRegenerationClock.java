package yagen.waitmydawn.yagens_mobs.butterfly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenSwarm;

@Mixin(MobEffectInstance.class)
abstract class LumenRegenerationClock {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;shouldApplyEffectTickThisTick(II)Z"))
    private boolean lumen$paidTickRate(MobEffect effect, int duration, int amplifier,
                                       Operation<Boolean> original, LivingEntity entity) {
        return original.call(effect, LumenSwarm.regenerationClock((MobEffectInstance) (Object) this, entity, duration), amplifier);
    }
}

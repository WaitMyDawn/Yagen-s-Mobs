package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenSwarm;

@Mixin(LivingEntity.class)
abstract class LumenLivingEntity {
    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void lumen$totem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && LumenSwarm.protectFromDeath((LivingEntity) (Object) this, source)) {
            callback.setReturnValue(true);
        }
    }
}

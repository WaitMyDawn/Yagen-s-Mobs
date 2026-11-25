package yagen.waitmydawn.yagens_mobs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatCombat;

@Mixin(LivingEntity.class)
abstract class KavatDamage {
    @ModifyArg(method="actuallyHurt", at=@At(value="INVOKE",target="Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"),index=1)
    private float yagens$reflect(float amount, @Local(argsOnly=true) DamageSource source) {
        KavatCombat.damage((LivingEntity)(Object)this, source, amount);
        return amount;
    }
}

package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenSwarm;

@Mixin(LivingEntity.class)
abstract class LumenLivingEntity {
    @WrapOperation(method = "dropExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void lumen$routeXp(ServerLevel level, Vec3 position, int xp, Operation<Void> award, Entity attacker) {
        int remaining = LumenSwarm.routeExperience(level, (LivingEntity) (Object) this, attacker, xp);
        if (remaining > 0) {
            award.call(level, position, remaining);
        }
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V")))
    private float lumen$shieldHealth(DamageContainer damage, Operation<Float> original, DamageSource source) {
        float remaining = original.call(damage);
        float shielded = LumenSwarm.shield((LivingEntity) (Object) this, source, remaining);
        if (shielded != remaining) {
            damage.setNewDamage(shielded);
        }
        return shielded;
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void lumen$totem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && LumenSwarm.protectFromDeath((LivingEntity) (Object) this, source)) {
            callback.setReturnValue(true);
        }
    }
}

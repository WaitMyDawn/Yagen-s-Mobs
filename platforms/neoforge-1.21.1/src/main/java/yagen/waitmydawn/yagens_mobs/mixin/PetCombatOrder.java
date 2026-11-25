package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.command.PetOrders;

@Mixin(Mob.class)
abstract class PetCombatOrder {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void yagens$honorTruce(LivingEntity target, CallbackInfo ci) {
        if (PetOrders.withheld((Mob)(Object)this, target)) ci.cancel();
    }
}

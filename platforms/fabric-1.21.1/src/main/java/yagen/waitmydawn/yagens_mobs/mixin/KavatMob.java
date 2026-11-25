package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.CompanionTravel;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatCombat;

@Mixin(Mob.class)
abstract class KavatMob {
    @ModifyVariable(method="setTarget", at=@At("HEAD"), argsOnly=true)
    private LivingEntity yagens$decoy(LivingEntity target) { return KavatCombat.redirect(target); }
    @Inject(method="tick", at=@At("TAIL"))
    private void yagens$trackFollower(CallbackInfo ci) { CompanionTravel.moved((Mob)(Object)this); }
}

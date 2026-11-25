package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatCombat;

@Mixin(Mob.class)
abstract class KavatMob {
    @ModifyVariable(method="setTarget", at=@At("HEAD"), argsOnly=true)
    private LivingEntity yagens$decoy(LivingEntity target) { return KavatCombat.redirect(target); }
}

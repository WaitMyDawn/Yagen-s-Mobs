package yagen.waitmydawn.yagens_mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;

@Mixin(LivingEntity.class)
abstract class BladderBreathing {
    // Only the air-supply check treats the enclosed medium as breathable.
    @ModifyExpressionValue(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean yagensMobs$needsAir(boolean submerged) {
        LivingEntity mob = (LivingEntity)(Object)this;
        return submerged && mob.level().getFluidState(BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ())).getType() != BladderFluid.MEDIUM;
    }
}

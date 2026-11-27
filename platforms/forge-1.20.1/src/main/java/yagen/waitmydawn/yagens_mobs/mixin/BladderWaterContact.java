package yagen.waitmydawn.yagens_mobs.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;

@Mixin(Entity.class)
abstract class BladderWaterContact {
    // Forge's legacy water queries compare fluid types by identity even for fluids in the water tag.
    @ModifyReturnValue(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At("RETURN"))
    private boolean yagensMobs$touchesWater(boolean original, TagKey<Fluid> tag, double motionScale) {
        return original || tag == FluidTags.WATER && ((Entity) (Object) this).isInFluidType(BladderFluid.TYPE.get());
    }

    @ModifyReturnValue(method = "isEyeInFluid", at = @At("RETURN"))
    private boolean yagensMobs$eyesInWater(boolean original, TagKey<Fluid> tag) {
        return original || tag == FluidTags.WATER && ((Entity) (Object) this).isEyeInFluidType(BladderFluid.TYPE.get());
    }

    @ModifyReturnValue(method = "getFluidHeight", at = @At("RETURN"))
    private double yagensMobs$waterHeight(double original, TagKey<Fluid> tag) {
        return tag == FluidTags.WATER ? Math.max(original, ((Entity) (Object) this).getFluidTypeHeight(BladderFluid.TYPE.get())) : original;
    }
}

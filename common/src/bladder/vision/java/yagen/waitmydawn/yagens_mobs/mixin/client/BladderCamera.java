package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;

@Mixin(Camera.class)
abstract class BladderCamera {
    @WrapOperation(method = "getFluidInCamera", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private boolean yagensMobs$realCameraWater(FluidState state, TagKey<Fluid> tag, Operation<Boolean> original) {
        // The membrane uses the water tag for movement, while only real water colors the view.
        return original.call(state, tag) && !(state.getType() instanceof BladderFluid);
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BladderView;

@Mixin(ScreenEffectRenderer.class)
abstract class BladderOverlay {
    @WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean yagensMobs$realWaterOverlay(LocalPlayer player, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(player, tag) && !BladderView.eyesInsideMembrane(player);
    }
}

package yagen.waitmydawn.yagens_mobs.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;

@Mixin(LightTexture.class)
abstract class BubbleSalamanderNightVision {
    // Resolve the equipment's vision at render time so it cannot replace or outlast a potion effect.
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"))
    private boolean yagensMobs$hasNightVision(boolean hasEffect) {
        var player = Minecraft.getInstance().player;
        return hasEffect || player != null && BubbleSalamander.isProvidingAir(player);
    }

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float yagensMobs$wearNightVision(LivingEntity entity, float partialTick, Operation<Float> original) {
        if (entity instanceof Player player && BubbleSalamander.isProvidingAir(player)) {
            return 1.0F;
        }
        return original.call(entity, partialTick);
    }
}

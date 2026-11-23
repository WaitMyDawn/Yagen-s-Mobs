package yagen.waitmydawn.yagens_mobs.butterfly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootingEnchantFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenLootContext;

@Mixin(LootingEnchantFunction.class)
abstract class LumenLootingFunction {
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootContext;getLootingModifier()I", remap = false))
    private int lumen$addLooting(LootContext context, Operation<Integer> original) {
        return original.call(context) + LumenLootContext.looting(context);
    }
}

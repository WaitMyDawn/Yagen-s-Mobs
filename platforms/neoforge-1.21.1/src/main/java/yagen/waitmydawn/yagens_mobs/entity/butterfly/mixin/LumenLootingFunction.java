package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenLootContext;

@Mixin(EnchantedCountIncreaseFunction.class)
abstract class LumenLootingFunction {
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int lumen$addLooting(Holder<Enchantment> enchantment, LivingEntity attacker, Operation<Integer> original,
                                  ItemStack loot, LootContext context) {
        return original.call(enchantment, attacker) + LumenLootContext.looting(context, enchantment);
    }
}

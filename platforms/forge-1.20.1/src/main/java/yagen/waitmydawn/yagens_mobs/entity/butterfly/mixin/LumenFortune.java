package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenLootContext;

@Mixin(ApplyBonusCount.class)
abstract class LumenFortune {
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I"))
    private int lumen$addFortune(Enchantment enchantment, ItemStack tool, Operation<Integer> original,
                                 ItemStack loot, LootContext context) {
        return original.call(enchantment, tool) + LumenLootContext.fortune(context, enchantment);
    }
}

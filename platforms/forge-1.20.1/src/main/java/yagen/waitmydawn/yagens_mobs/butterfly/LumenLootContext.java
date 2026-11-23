package yagen.waitmydawn.yagens_mobs.butterfly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public interface LumenLootContext {
    int lumen$fortune(ServerPlayer player);

    int lumen$looting(ServerPlayer player);

    static int fortune(LootContext context, Enchantment enchantment) {
        if (enchantment == Enchantments.BLOCK_FORTUNE && context.getParamOrNull(LootContextParams.BLOCK_STATE) != null
                && context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            return ((LumenLootContext) context).lumen$fortune(player);
        }
        return 0;
    }

    static int looting(LootContext context) {
        if (context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof ServerPlayer player) {
            return ((LumenLootContext) context).lumen$looting(player);
        }
        return 0;
    }
}

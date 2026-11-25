package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public interface LumenLootContext {
    int lumen$fortune(ServerPlayer player);

    int lumen$looting(ServerPlayer player);

    static int fortune(LootContext context, Holder<Enchantment> enchantment) {
        if (enchantment.is(Enchantments.FORTUNE) && context.getOptional(LootContextParams.BLOCK_STATE) != null
                && context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            return ((LumenLootContext) context).lumen$fortune(player);
        }
        return 0;
    }

    static int looting(LootContext context, Holder<Enchantment> enchantment) {
        if (enchantment.is(Enchantments.LOOTING)
                && context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof ServerPlayer player) {
            return ((LumenLootContext) context).lumen$looting(player);
        }
        return 0;
    }
}

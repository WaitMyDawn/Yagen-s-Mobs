package yagen.waitmydawn.yagens_mobs.butterfly.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenLootContext;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenSwarm;

@Mixin(LootContext.class)
abstract class LumenLootCache implements LumenLootContext {
    @Unique
    private int lumen$fortuneBonus = -1;
    @Unique
    private int lumen$lootingBonus = -1;

    @Override
    public int lumen$fortune(ServerPlayer player) {
        if (lumen$fortuneBonus < 0) {
            lumen$fortuneBonus = LumenSwarm.spendEnchantmentBonus(player);
        }
        return lumen$fortuneBonus;
    }

    @Override
    public int lumen$looting(ServerPlayer player) {
        // A single loot roll may ask for the same level in several pools and conditions.
        if (lumen$lootingBonus < 0) {
            lumen$lootingBonus = LumenSwarm.spendEnchantmentBonus(player);
        }
        return lumen$lootingBonus;
    }
}

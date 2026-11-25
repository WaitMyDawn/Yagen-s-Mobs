package yagen.waitmydawn.yagens_mobs.loot;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenLootContext;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record AttackerEnchantmentLevel(Holder<Enchantment> enchantment) implements NumberProvider {
    private static final MapCodec<AttackerEnchantmentLevel> CODEC = Enchantment.CODEC.fieldOf("enchantment")
            .xmap(AttackerEnchantmentLevel::new, AttackerEnchantmentLevel::enchantment);
    private static final DeferredRegister<LootNumberProviderType> TYPES =
            DeferredRegister.create(Registries.LOOT_NUMBER_PROVIDER_TYPE, ModIdentity.ID);
    private static final DeferredHolder<LootNumberProviderType, LootNumberProviderType> TYPE =
            TYPES.register("attacker_enchantment_level", () -> new LootNumberProviderType(CODEC));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public float getFloat(LootContext context) {
        return getInt(context);
    }

    @Override
    public int getInt(LootContext context) {
        return context.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(enchantment, attacker) + LumenLootContext.looting(context, enchantment)
                : 0;
    }

    @Override
    public LootNumberProviderType getType() {
        return TYPE.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ATTACKING_ENTITY);
    }
}

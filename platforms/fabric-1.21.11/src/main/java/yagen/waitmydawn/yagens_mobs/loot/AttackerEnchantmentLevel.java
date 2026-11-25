package yagen.waitmydawn.yagens_mobs.loot;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.Holder;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenLootContext;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record AttackerEnchantmentLevel(Holder<Enchantment> enchantment) implements NumberProvider {
    private static final MapCodec<AttackerEnchantmentLevel> CODEC = Enchantment.CODEC.fieldOf("enchantment")
            .xmap(AttackerEnchantmentLevel::new, AttackerEnchantmentLevel::enchantment);
    private static final LootNumberProviderType TYPE = Registry.register(BuiltInRegistries.LOOT_NUMBER_PROVIDER_TYPE,
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "attacker_enchantment_level"),
            new LootNumberProviderType(CODEC));

    public static void register() {
    }

    @Override
    public float getFloat(LootContext context) {
        return getInt(context);
    }

    @Override
    public int getInt(LootContext context) {
        return context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(enchantment, attacker) + LumenLootContext.looting(context, enchantment)
                : 0;
    }

    @Override
    public LootNumberProviderType getType() {
        return TYPE;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ATTACKING_ENTITY);
    }
}

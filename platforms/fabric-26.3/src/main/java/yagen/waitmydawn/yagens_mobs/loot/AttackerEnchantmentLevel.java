package yagen.waitmydawn.yagens_mobs.loot;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenLootContext;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record AttackerEnchantmentLevel(Holder<Enchantment> enchantment) implements ContextIntProvider,
        net.minecraft.world.level.storage.loot.LootContextUser {
    private static final MapCodec<AttackerEnchantmentLevel> CODEC = Enchantment.CODEC.fieldOf("enchantment")
            .xmap(AttackerEnchantmentLevel::new, AttackerEnchantmentLevel::enchantment);

    public static void register() {
        Registry.register(BuiltInRegistries.CONTEXT_INT_PROVIDER_TYPE,
                Identifier.fromNamespaceAndPath(ModIdentity.ID, "attacker_enchantment_level"), CODEC);
    }

    @Override
    public int getIntUnsafe(LootContext context) {
        return context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(enchantment, attacker) + LumenLootContext.looting(context, enchantment)
                : 0;
    }

    @Override
    public MapCodec<? extends ContextIntProvider> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<net.minecraft.util.context.ContextKey<?>> getReferencedContextParams() {
        return java.util.Set.of(LootContextParams.ATTACKING_ENTITY);
    }
}

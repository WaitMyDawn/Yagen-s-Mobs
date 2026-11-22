package yagen.waitmydawn.yagens_mobs.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record AttackerEnchantmentLevel(Enchantment enchantment) implements NumberProvider {
    private static final Serializer<AttackerEnchantmentLevel> SERIALIZER = new Serializer<>() {
        @Override
        public void serialize(JsonObject json, AttackerEnchantmentLevel provider, JsonSerializationContext context) {
            json.addProperty("enchantment", BuiltInRegistries.ENCHANTMENT.getKey(provider.enchantment()).toString());
        }

        @Override
        public AttackerEnchantmentLevel deserialize(JsonObject json, JsonDeserializationContext context) {
            var id = new ResourceLocation(json.get("enchantment").getAsString());
            var enchantment = BuiltInRegistries.ENCHANTMENT.getOptional(id)
                    .orElseThrow(() -> new com.google.gson.JsonSyntaxException("Unknown enchantment " + id));
            return new AttackerEnchantmentLevel(enchantment);
        }
    };
    private static final DeferredRegister<LootNumberProviderType> TYPES =
            DeferredRegister.create(Registries.LOOT_NUMBER_PROVIDER_TYPE, ModIdentity.ID);
    private static final RegistryObject<LootNumberProviderType> TYPE =
            TYPES.register("attacker_enchantment_level", () -> new LootNumberProviderType(SERIALIZER));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public float getFloat(LootContext context) {
        return getInt(context);
    }

    @Override
    public int getInt(LootContext context) {
        return context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(enchantment, attacker)
                : 0;
    }

    @Override
    public LootNumberProviderType getType() {
        return TYPE.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.KILLER_ENTITY);
    }
}

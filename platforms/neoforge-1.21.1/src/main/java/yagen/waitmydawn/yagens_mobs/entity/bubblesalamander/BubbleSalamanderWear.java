package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID)
public final class BubbleSalamanderWear {
    private BubbleSalamanderWear() {
    }

    @SubscribeEvent
    public static void keepAirFull(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && BubbleSalamander.isProvidingAir(player)) {
            player.setAirSupply(player.getMaxAirSupply());
            if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                player.removeEffect(MobEffects.DIG_SLOWDOWN);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventMiningFatigue(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().equals(MobEffects.DIG_SLOWDOWN)
                && event.getEntity() instanceof ServerPlayer player && BubbleSalamander.isProvidingAir(player)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        detach(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            detach(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDimensionChange(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            detach(player);
        }
    }

    private static void detach(Player player) {
        BubbleSalamander mob = BubbleSalamander.wornBy(player);
        if (mob != null) {
            mob.detach();
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Remove.TYPE, Remove.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                detach(player);
            }
        });
    }

    public record Remove() implements CustomPacketPayload {
        public static final Remove INSTANCE = new Remove();
        private static final Type<Remove> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "remove_bubble_salamander"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Remove> type() {
            return TYPE;
        }
    }
}

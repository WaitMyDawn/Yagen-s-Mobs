package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID)
public final class BubbleSalamanderWear {
    private BubbleSalamanderWear() {
    }

    @SubscribeEvent
    public static void keepAirFull(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player && BubbleSalamander.isProvidingAir(player)) {
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
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
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

    public static void handleRemove(Remove message, ServerPlayer player) {
        if (player != null) {
            detach(player);
        }
    }

    public record Remove() {
        public static final Remove INSTANCE = new Remove();
    }
}

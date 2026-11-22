package yagen.waitmydawn.yagens_mobs.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import yagen.waitmydawn.yagens_mobs.network.FabricPayloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class BubbleSalamanderWear {
    private BubbleSalamanderWear() {
    }

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            for (var player : level.players()) {
                if (BubbleSalamander.isProvidingAir(player)) {
                    player.setAirSupply(player.getMaxAirSupply());
                    player.removeEffect(MobEffects.MINING_FATIGUE);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> detach(handler.player));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> detach(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) detach(player);
        });
        FabricPayloads.toServer(Remove.TYPE, Remove.CODEC, (message, player) -> detach(player));
    }

    private static void detach(Player player) {
        BubbleSalamander mob = BubbleSalamander.wornBy(player);
        if (mob != null) {
            mob.detach();
        }
    }

    public record Remove() implements CustomPacketPayload {
        public static final Remove INSTANCE = new Remove();
        private static final Type<Remove> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "remove_bubble_salamander"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Remove> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Remove> type() {
            return TYPE;
        }
    }
}

package yagen.waitmydawn.yagens_mobs.silk;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class RockSilkPackets {
    private static final Map<ServerPlayer, Integer> LAST_GRAB = new WeakHashMap<>();

    private RockSilkPackets() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Grab.TYPE, Grab.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && Float.isFinite(payload.yaw())
                    && Float.isFinite(payload.pitch()) && Math.abs(payload.pitch()) <= 90
                    && player.containerMenu == player.inventoryMenu
                    && !(player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                    .getItem() instanceof SilkLauncher)) {
                tryGrab(player, Vec3.directionFromRotation(payload.pitch(), payload.yaw()));
            }
        });
        registrar.playToServer(Input.TYPE, Input.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof RockSilkGrip grip
                    && grip.getId() == payload.vehicle()) {
                grip.updateInput(player, payload.keys());
            }
        });
    }

    private static void tryGrab(ServerPlayer player, Vec3 view) {
        Integer last = LAST_GRAB.get(player);
        if (last != null && player.tickCount - last < 3) {
            return;
        }
        LAST_GRAB.put(player, player.tickCount);
        var target = RockSilkInteractions.aimedLine(player, view, player.entityInteractionRange());
        if (target != null) {
            RockSilkGrip.grab(player, target);
        }
    }

    public record Grab(float yaw, float pitch, boolean offhand) implements CustomPacketPayload {
        private static final Type<Grab> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "silk_grab"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Grab> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Grab::yaw, ByteBufCodecs.FLOAT, Grab::pitch, ByteBufCodecs.BOOL, Grab::offhand, Grab::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Input(int vehicle, int keys) implements CustomPacketPayload {
        private static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "silk_input"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::vehicle, ByteBufCodecs.VAR_INT, Input::keys, Input::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}


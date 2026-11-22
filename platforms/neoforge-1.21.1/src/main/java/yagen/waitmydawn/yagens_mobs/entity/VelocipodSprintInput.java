package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record VelocipodSprintInput(int vehicle, boolean held) implements CustomPacketPayload {
    private static final Type<VelocipodSprintInput> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "velocipod_sprint"));
    private static final StreamCodec<RegistryFriendlyByteBuf, VelocipodSprintInput> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VelocipodSprintInput::vehicle, ByteBufCodecs.BOOL, VelocipodSprintInput::held,
            VelocipodSprintInput::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer rider && rider.getVehicle() instanceof Velocipod mount
                    && mount.getId() == payload.vehicle()) {
                mount.setRiderSprint(rider, payload.held());
            }
        });
    }

    @Override
    public Type<VelocipodSprintInput> type() {
        return TYPE;
    }
}


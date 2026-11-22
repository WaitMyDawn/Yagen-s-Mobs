package yagen.waitmydawn.yagens_mobs.entity;

import yagen.waitmydawn.yagens_mobs.network.FabricPayloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record VelocipodSprintInput(int vehicle, boolean held) implements CustomPacketPayload {
    private static final Type<VelocipodSprintInput> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "velocipod_sprint"));
    private static final StreamCodec<RegistryFriendlyByteBuf, VelocipodSprintInput> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VelocipodSprintInput::vehicle, ByteBufCodecs.BOOL, VelocipodSprintInput::held,
            VelocipodSprintInput::new);

    public static void register() {
        FabricPayloads.toServer(TYPE, CODEC, (payload, rider) -> {
            if (rider.getVehicle() instanceof Velocipod mount
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


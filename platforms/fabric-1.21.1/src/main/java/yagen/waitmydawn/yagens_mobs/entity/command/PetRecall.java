package yagen.waitmydawn.yagens_mobs.entity.command;

import yagen.waitmydawn.yagens_mobs.network.FabricPayloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record PetRecall(int[] entities) implements CustomPacketPayload {
    private static final Type<PetRecall> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "pet_disengage"));
    private static final StreamCodec<RegistryFriendlyByteBuf, PetRecall> CODEC = StreamCodec.of(
            (buffer, message) -> buffer.writeVarIntArray(message.entities()),
            buffer -> new PetRecall(buffer.readVarIntArray(PetOrders.BATCH)));

    public static void register() {
        FabricPayloads.toServer(TYPE, CODEC, (message, owner) -> PetOrders.recall(owner, message.entities()));
    }

    @Override
    public Type<PetRecall> type() {
        return TYPE;
    }
}

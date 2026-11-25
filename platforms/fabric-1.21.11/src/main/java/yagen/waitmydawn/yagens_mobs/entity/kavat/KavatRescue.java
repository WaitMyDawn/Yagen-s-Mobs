package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record KavatRescue() implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ModIdentity.ID, "vasca_rescue");
    public static final Type<KavatRescue> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, KavatRescue> CODEC = StreamCodec.unit(new KavatRescue());
    public Type<KavatRescue> type() { return TYPE; }
}

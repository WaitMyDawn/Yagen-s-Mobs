package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record IncubatorPage(int menu, CompoundTag view) implements CustomPacketPayload {
    public static final Type<IncubatorPage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "incubator_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IncubatorPage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IncubatorPage::menu, ByteBufCodecs.COMPOUND_TAG, IncubatorPage::view, IncubatorPage::new);
    public Type<IncubatorPage> type() { return TYPE; }
}

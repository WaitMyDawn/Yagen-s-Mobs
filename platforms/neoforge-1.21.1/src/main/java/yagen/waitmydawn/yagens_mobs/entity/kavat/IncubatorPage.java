package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public record IncubatorPage(int menu, CompoundTag view) implements CustomPacketPayload {
    private static final Type<IncubatorPage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "incubator_page"));
    private static final StreamCodec<RegistryFriendlyByteBuf, IncubatorPage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IncubatorPage::menu, ByteBufCodecs.COMPOUND_TAG, IncubatorPage::view, IncubatorPage::new);
    public Type<IncubatorPage> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (message, context) -> {
            if (context.player().containerMenu instanceof IncubatorMenu menu && menu.containerId == message.menu())
                menu.receive(message.view());
        });
    }
}

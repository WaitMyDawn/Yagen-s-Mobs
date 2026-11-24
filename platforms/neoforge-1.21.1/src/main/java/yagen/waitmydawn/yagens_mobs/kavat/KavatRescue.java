package yagen.waitmydawn.yagens_mobs.kavat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID)
public record KavatRescue() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "vasca_rescue");
    private static final Type<KavatRescue> TYPE = new Type<>(ID);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        StreamCodec<RegistryFriendlyByteBuf, KavatRescue> codec = StreamCodec.unit(new KavatRescue());
        event.registrar("1").playToClient(TYPE, codec, (payload, context) -> KavatRescueView.flash());
    }
    public Type<KavatRescue> type() { return TYPE; }
}

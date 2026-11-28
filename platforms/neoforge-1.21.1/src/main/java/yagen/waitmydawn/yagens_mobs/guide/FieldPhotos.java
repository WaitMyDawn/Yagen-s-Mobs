package yagen.waitmydawn.yagens_mobs.guide;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class FieldPhotos {
    public record Request(String key) implements CustomPacketPayload {
        static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("yagens_mobs", "request_portrait"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.of((b, v) -> b.writeUtf(v.key, 128), b -> new Request(b.readUtf(128)));
        public Type<Request> type() {
            return TYPE;
        }
    }
    public record Picture(String key, byte[] png) implements CustomPacketPayload {
        static final Type<Picture> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("yagens_mobs", "portrait"));
        static final StreamCodec<RegistryFriendlyByteBuf, Picture> CODEC = StreamCodec.of((b, v) -> {
            b.writeUtf(v.key, 128);
            b.writeByteArray(v.png);
        }, b -> new Picture(b.readUtf(128), b.readByteArray(PortraitStore.MAX_BYTES)));
        public Type<Picture> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var net = event.registrar("2").optional();
        net.playToClient(Picture.TYPE, Picture.CODEC,
                (packet, context) -> yagen.waitmydawn.yagens_mobs.guide.client.PortraitTextures.accept(packet.key(), packet.png()));
        net.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            PortraitStore.request(player, packet.key(), bytes -> PacketDistributor.sendToPlayer(player, new Picture(packet.key(), bytes)));
        });
    }

    public static void requestFromClient(String key) {
        PacketDistributor.sendToServer(new Request(key));
    }

    private FieldPhotos() {}
}

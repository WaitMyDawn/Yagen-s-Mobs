package yagen.waitmydawn.yagens_mobs.guide;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import yagen.waitmydawn.yagens_mobs.guide.client.PortraitTextures;

public final class FieldPhotos {
    private record Request(String key) {}
    private record Picture(String key, byte[] png) {}

    static void register() {
        var channel = FieldGuide.channel();
        channel.messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> buf.writeUtf(packet.key(), 128))
                .decoder(buf -> new Request(buf.readUtf(128)))
                .consumerMainThread((packet, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) PortraitStore.request(player, packet.key(), bytes ->
                            channel.send(PacketDistributor.PLAYER.with(() -> player), new Picture(packet.key(), bytes)));
                }).add();
        channel.messageBuilder(Picture.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> {
                    buf.writeUtf(packet.key(), 128);
                    buf.writeByteArray(packet.png());
                })
                .decoder(buf -> new Picture(buf.readUtf(128), buf.readByteArray(PortraitStore.MAX_BYTES)))
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> PortraitTextures.accept(packet.key(), packet.png())))
                .add();
    }

    public static void requestFromClient(String key) {
        FieldGuide.channel().sendToServer(new Request(key));
    }

    private FieldPhotos() {}
}

package yagen.waitmydawn.yagens_mobs.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import yagen.waitmydawn.yagens_mobs.client.LegacyClientPackets;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamanderWear;
import yagen.waitmydawn.yagens_mobs.entity.VelocipodSprintInput;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageRoutes;
import yagen.waitmydawn.yagens_mobs.salvage.SalvageClient;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;
import yagen.waitmydawn.yagens_mobs.weasel.WeaselPackets;
import yagen.waitmydawn.yagens_mobs.weasel.client.WeaselClient;

public final class LegacyNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModIdentity.ID, "play"), () -> VERSION, VERSION::equals, VERSION::equals);

    private LegacyNetwork() {}

    public static void register() {
        toServer(9, WeaselPackets.Request.class, (msg, buf) -> buf.writeNbt(msg.tag()),
                buf -> new WeaselPackets.Request(buf.readNbt()), WeaselPackets::handle);
        CHANNEL.messageBuilder(WeaselPackets.Opening.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> buf.writeNbt(msg.tag()))
                .decoder(buf -> new WeaselPackets.Opening(buf.readNbt()))
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> WeaselClient.open(msg.tag()))).add();
        toServer(0, StationPackets.Request.class, (msg, buf) -> buf.writeNbt(msg.data()),
                buf -> new StationPackets.Request(buf.readNbt()), StationPackets::handleRequest);
        toServer(1, RockSilkPackets.Grab.class, (msg, buf) -> {
            buf.writeFloat(msg.yaw());
            buf.writeFloat(msg.pitch());
            buf.writeBoolean(msg.offhand());
        }, buf -> new RockSilkPackets.Grab(buf.readFloat(), buf.readFloat(), buf.readBoolean()), RockSilkPackets::handleGrab);
        toServer(2, RockSilkPackets.Input.class, (msg, buf) -> {
            buf.writeVarInt(msg.vehicle());
            buf.writeVarInt(msg.keys());
        }, buf -> new RockSilkPackets.Input(buf.readVarInt(), buf.readVarInt()), RockSilkPackets::handleInput);
        toServer(3, VelocipodSprintInput.class, (msg, buf) -> {
            buf.writeVarInt(msg.vehicle());
            buf.writeBoolean(msg.held());
        }, buf -> new VelocipodSprintInput(buf.readVarInt(), buf.readBoolean()), VelocipodSprintInput::handle);
        toServer(4, BubbleSalamanderWear.Remove.class, (msg, buf) -> {},
                buf -> BubbleSalamanderWear.Remove.INSTANCE, BubbleSalamanderWear::handleRemove);
        CHANNEL.messageBuilder(StationPackets.Snapshot.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> buf.writeNbt(msg.data()))
                .decoder(buf -> new StationPackets.Snapshot(buf.readNbt()))
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> LegacyClientPackets.updateStation(msg))).add();
        toServer(7, SalvageRoutes.Request.class, (msg, buf) -> buf.writeNbt(msg.data()),
                buf -> new SalvageRoutes.Request(buf.readNbt()), SalvageRoutes::handle);
        CHANNEL.messageBuilder(SalvageRoutes.Snapshot.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> buf.writeNbt(msg.data()))
                .decoder(buf -> new SalvageRoutes.Snapshot(buf.readNbt()))
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> SalvageClient.receive(msg))).add();
        CHANNEL.messageBuilder(StationPackets.MailName.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> { buf.writeVarInt(msg.containerId()); buf.writeUtf(msg.name()); })
                .decoder(buf -> new StationPackets.MailName(buf.readVarInt(), buf.readUtf()))
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> LegacyClientPackets.updateMailName(msg))).add();
    }

    private static <T> void toServer(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encode,
                                     Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER).encoder(encode).decoder(decode)
                .consumerMainThread((message, context) -> handle.accept(message, context.get().getSender())).add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static double entityReach(Player player) {
        return player.getEntityReach();
    }
}

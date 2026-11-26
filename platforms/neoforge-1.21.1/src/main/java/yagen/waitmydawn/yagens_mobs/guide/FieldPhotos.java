package yagen.waitmydawn.yagens_mobs.guide;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class FieldPhotos {
    private static final int MAX_BYTES = 65536;
    private static final Map<Path, CompletableFuture<byte[]>> pending = new HashMap<>();
    private static final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, new ArrayBlockingQueue<>(96),
                    task -> Thread.ofPlatform().daemon().name("Field guide portraits").unstarted(task));
    private static final Set<String> keys = new HashSet<>();
    private static final Map<ServerPlayer, Budget> budgets = new WeakHashMap<>();
    private record Budget(long tick, int count) {}
    static boolean valid(String key) {
        if (keys.isEmpty())
            for (String species : FieldNotes.SPECIES)
                for (String variant : FieldNotes.variants(species)) keys.add(species + "/" + variant);
        return keys.contains(key);
    }

    static synchronized CompletableFuture<byte[]> prepare(MinecraftServer server, String key) {
        if (!valid(key))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown field-guide variant"));
        Path path = server.getWorldPath(LevelResource.ROOT)
                            .resolve("data/yagens_mobs/field_guide/v1")
                            .resolve(key + ".png");
        var active = pending.get(path);
        if (active != null)
            return active;
        var job = new CompletableFuture<byte[]>();
        pending.put(path, job);
        try {
            worker.execute(() -> {
                try {
                    byte[] bytes = Files.isRegularFile(path) && Files.size(path) <= MAX_BYTES ? Files.readAllBytes(path)
                                                                                              : null;
                    if (!isPortrait(bytes)) {
                        bytes = SpecimenPortrait.render(key);
                        if (bytes.length > MAX_BYTES)
                            throw new IOException("Oversized portrait");
                        Files.createDirectories(path.getParent());
                        Path temp = Files.createTempFile(path.getParent(), "portrait-", ".tmp");
                        try {
                            Files.write(temp, bytes);
                            try {
                                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (AtomicMoveNotSupportedException e) {
                                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } finally {
                            Files.deleteIfExists(temp);
                        }
                    }
                    job.complete(bytes);
                } catch (Exception ex) {
                    LogUtils.getLogger().warn("Could not prepare field guide portrait {}", key, ex);
                    job.completeExceptionally(ex);
                } finally {
                    synchronized (FieldPhotos.class) {
                        pending.remove(path, job);
                    }
                }
            });
        } catch (RejectedExecutionException busy) {
            pending.remove(path);
            job.completeExceptionally(busy);
        }
        return job;
    }
    private static boolean isPortrait(byte[] bytes) {
        if (bytes == null || bytes.length < 45)
            return false;
        var header = java.nio.ByteBuffer.wrap(bytes);
        return header.getLong(0) == 0x89504E470D0A1A0AL && header.getInt(12) == 0x49484452 && header.getInt(16) == 128
                && header.getInt(20) == 96 && header.getInt(bytes.length - 8) == 0x49454E44;
    }
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
        }, b -> new Picture(b.readUtf(128), b.readByteArray(MAX_BYTES)));
        public Type<Picture> type() {
            return TYPE;
        }
    }
    static void register(RegisterPayloadHandlersEvent event) {
        var net = event.registrar("2").optional();
        net.playToClient(Picture.TYPE, Picture.CODEC,
                (p, c) -> yagen.waitmydawn.yagens_mobs.guide.client.PortraitTextures.accept(p));
        net.playToServer(Request.TYPE, Request.CODEC, (p, c) -> {
            var player = (ServerPlayer) c.player();
            if (!FieldGuide.notes(player).contains(p.key) || !valid(p.key))
                return;
            var server = player.serverLevel().getServer();
            long tick = server.overworld().getGameTime();
            var b = budgets.get(player);
            int count = b == null || tick - b.tick >= 20 ? 0 : b.count;
            if (count >= 16)
                return;
            budgets.put(player, new Budget(count == 0 ? tick : b.tick, count + 1));
            prepare(server, p.key).thenAccept(bytes -> server.execute(() -> {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                    PacketDistributor.sendToPlayer(player, new Picture(p.key, bytes));
            }));
        });
    }
    private FieldPhotos() {}
}

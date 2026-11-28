package yagen.waitmydawn.yagens_mobs.guide;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

final class PortraitStore {
    static final int MAX_BYTES = 65536;
    private static final Map<Path, CompletableFuture<byte[]>> pending = new HashMap<>();
    private static final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, new ArrayBlockingQueue<>(96),
                    task -> {
                        var thread = new Thread(task, "Field guide portraits");
                        thread.setDaemon(true);
                        return thread;
                    });
    private static final Set<String> keys = FieldNotes.SPECIES.stream()
            .flatMap(species -> FieldNotes.variants(species).stream().map(variant -> species + "/" + variant))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Map<ServerPlayer, Budget> budgets = new WeakHashMap<>();
    private record Budget(long tick, int count) {}
    static boolean valid(String key) {
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
                    synchronized (PortraitStore.class) {
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


    static void request(ServerPlayer player, String key, java.util.function.Consumer<byte[]> send) {
        if (!valid(key) || !FieldGuide.notes(player).contains(key)) return;
        var server = FieldGuide.level(player).getServer();
        long tick = server.overworld().getGameTime();
        var budget = budgets.get(player);
        int count = budget == null || tick - budget.tick >= 20 ? 0 : budget.count;
        if (count >= 16) return;
        budgets.put(player, new Budget(count == 0 ? tick : budget.tick, count + 1));
        prepare(server, key).thenAccept(bytes -> server.execute(() -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) send.accept(bytes);
        }));
    }

    private PortraitStore() {}
}

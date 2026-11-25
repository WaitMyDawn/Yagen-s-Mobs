package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;

public final class BubbleSalamanderTextures extends SimplePreparableReloadListener<BubbleSalamanderTextures.Palettes> {
    public static final BubbleSalamanderTextures INSTANCE = new BubbleSalamanderTextures();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = 256;
    private static final int CACHE_SIZE = 32;
    private static final int MAX_GENERATING = 2;
    private static final int REGION_GRID = 64;
    private static final int PATCH_WIDTH = 5;
    private static final BubbleSalamanderColor[] MIX_PALETTE = {
            BubbleSalamanderColor.GREEN, BubbleSalamanderColor.PINK, BubbleSalamanderColor.YELLOW,
            BubbleSalamanderColor.BROWN, BubbleSalamanderColor.RED, BubbleSalamanderColor.BLUE
    };
    private static final ResourceLocation[] COLORS = java.util.Arrays.stream(BubbleSalamanderColor.values())
            .map(color -> color == BubbleSalamanderColor.GREEN ? id("textures/entity/bubble_salamander.png")
                    : id("textures/entity/bubble_salamander/" + color.id() + ".png"))
            .toArray(ResourceLocation[]::new);
    private static final ResourceLocation[] LEGACY_TEXTURES = java.util.stream.IntStream
            .range(0, BubbleSalamanderColor.LEGACY_MIXED_PATTERNS)
            .mapToObj(index -> id("textures/entity/bubble_salamander/mixed_" + index + ".png"))
            .toArray(ResourceLocation[]::new);
    // RenderType memoizes texture IDs. Reusing slots also bounds that cache.
    private static final ResourceLocation[] SLOT_TEXTURES = java.util.stream.IntStream.range(0, CACHE_SIZE)
            .mapToObj(index -> id("runtime/bubble_salamander/" + index)).toArray(ResourceLocation[]::new);

    private final Map<Long, Entry> entries = new HashMap<>();
    private final boolean[] occupied = new boolean[CACHE_SIZE];
    @Nullable private Palettes palettes;
    private int generating;
    private boolean needsRoom;
    private long clock;

    private BubbleSalamanderTextures() {
    }

    ResourceLocation texture(BubbleSalamanderColor color, long seed) {
        if (color != BubbleSalamanderColor.MIXED) {
            return COLORS[color.ordinal()];
        }
        if (seed >= 0 && seed < LEGACY_TEXTURES.length) {
            return LEGACY_TEXTURES[(int) seed];
        }
        Entry entry = entries.get(seed);
        if (entry != null) {
            entry.lastSeen = clock;
            if (entry.ready) {
                return SLOT_TEXTURES[entry.slot];
            }
        } else if (palettes != null) {
            if (entries.size() == CACHE_SIZE) {
                needsRoom = true;
            } else if (generating < MAX_GENERATING) {
                request(seed, palettes);
            }
        }
        return LEGACY_TEXTURES[Math.floorMod(Long.hashCode(seed), LEGACY_TEXTURES.length)];
    }

    private void request(long seed, Palettes input) {
        int slot = 0;
        while (occupied[slot]) {
            slot++;
        }
        occupied[slot] = true;
        Entry entry = new Entry(slot, clock);
        entries.put(seed, entry);
        generating++;
        Minecraft game = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> paint(input, seed), Util.backgroundExecutor())
                .whenComplete((pixels, error) -> game.execute(() -> finish(seed, entry, pixels, error)));
    }

    private void finish(long seed, Entry entry, int @Nullable [] pixels, @Nullable Throwable error) {
        generating--;
        if (entries.get(seed) != entry) {
            return;
        }
        if (error != null || pixels == null) {
            evict(entry);
            entries.remove(seed);
            LOGGER.warn("Could not generate a Bubble Salamander coat", error);
            return;
        }

        NativeImage image = new NativeImage(SIZE, SIZE, false);
        DynamicTexture texture = null;
        try {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    image.setPixelRGBA(x, y, pixels[y * SIZE + x]);
                }
            }
            texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(SLOT_TEXTURES[entry.slot], texture);
            entry.ready = true;
        } catch (RuntimeException failure) {
            if (texture == null) {
                image.close();
            } else {
                texture.close();
            }
            evict(entry);
            entries.remove(seed);
            LOGGER.warn("Could not upload a Bubble Salamander coat", failure);
        }
    }

    void tick() {
        clock++;
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        boolean reclaim = needsRoom;
        needsRoom = false;
        // Release textures between frames, never while the entity render batch can still reference them.
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
            Entry entry = it.next();
            long unused = clock - entry.lastSeen;
            if (unused >= 200 || reclaim && unused >= 20) {
                evict(entry);
                it.remove();
                reclaim = false;
            }
        }
    }

    private void clear() {
        entries.values().forEach(this::evict);
        entries.clear();
        needsRoom = false;
    }

    private void evict(Entry entry) {
        occupied[entry.slot] = false;
        if (entry.ready) {
            Minecraft.getInstance().getTextureManager().release(SLOT_TEXTURES[entry.slot]);
        }
    }

    @Override
    protected @Nullable Palettes prepare(ResourceManager manager, ProfilerFiller profiler) {
        try {
            int[][] colors = new int[MIX_PALETTE.length][];
            for (int index = 0; index < MIX_PALETTE.length; index++) {
                colors[index] = readPixels(manager, COLORS[MIX_PALETTE[index].ordinal()]);
            }
            return new Palettes(readPixels(manager, id("textures/entity/bubble_salamander/procedural_regions.png")), colors);
        } catch (IOException error) {
            LOGGER.warn("Bubble Salamander procedural textures could not be prepared", error);
            return null;
        }
    }

    @Override
    protected void apply(@Nullable Palettes prepared, ResourceManager manager, ProfilerFiller profiler) {
        clear();
        palettes = prepared;
    }

    private static int[] readPixels(ResourceManager manager, ResourceLocation location) throws IOException {
        try (var input = manager.open(location); var image = NativeImage.read(input)) {
            if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
                throw new IOException("Incompatible Bubble Salamander texture dimensions for " + location);
            }
            int[] pixels = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    pixels[y * SIZE + x] = image.getPixelRGBA(x, y);
                }
            }
            return pixels;
        }
    }

    private static int[] paint(Palettes input, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int[] order = {0, 1, 2, 3, 4, 5};
        for (int index = 0; index < 3; index++) {
            int swap = index + random.nextInt(order.length - index);
            int value = order[index];
            order[index] = order[swap];
            order[swap] = value;
        }
        int[] primary = input.colors[order[0]];
        int[] pixels = primary.clone();
        byte[] pigment = new byte[REGION_GRID * REGION_GRID * REGION_GRID];
        for (int index = 0; index < pixels.length; index++) {
            int mask = input.regions[index];
            int area = mask >>> 6 & 3;
            if (area == 0 || area == 3 || (primary[index] >>> 24) == 0) {
                continue;
            }
            int x = mask & 63;
            int y = mask >>> 8 & 63;
            int z = mask >>> 16 & 63;
            int coordinate = (x * REGION_GRID + y) * REGION_GRID + z;
            int color = pigment[coordinate];
            if (color == 0) {
                color = 1 + pigmentAt(seed, x, y, z);
                pigment[coordinate] = (byte) color;
            }
            int selected = input.colors[order[color - 1]][index];
            pixels[index] = area == 2 ? tintMembrane(primary[index], selected) : selected;
        }
        return pixels;
    }

    private static int pigmentAt(long seed, int x, int y, int z) {
        int column = x / PATCH_WIDTH;
        int row = y / PATCH_WIDTH;
        int depth = z / PATCH_WIDTH;
        int nearest = Integer.MAX_VALUE;
        int pigment = 0;
        for (int bx = column - 1; bx <= column + 1; bx++) {
            for (int by = row - 1; by <= row + 1; by++) {
                for (int bz = depth - 1; bz <= depth + 1; bz++) {
                    long cell = mixBits(seed ^ (bx * 0x9E3779B97F4A7C15L)
                            ^ (by * 0xC2B2AE3D27D4EB4FL) ^ (bz * 0x165667B19E3779F9L));
                    int dx = bx * PATCH_WIDTH + 1 + (int) (cell & 3) - x;
                    int dy = by * PATCH_WIDTH + 1 + (int) (cell >>> 2 & 3) - y;
                    int dz = bz * PATCH_WIDTH + 1 + (int) (cell >>> 4 & 3) - z;
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearest) {
                        nearest = distance;
                        pigment = (int) ((cell >>> 16 & 255) % 3);
                    }
                }
            }
        }
        return pigment;
    }

    private static long mixBits(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int tintMembrane(int primary, int accent) {
        int result = primary & 0xff000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int shade = (5 * (primary >>> shift & 255) + 3 * (accent >>> shift & 255)) / 8;
            result |= shade << shift;
        }
        return result;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ModIdentity.ID, path);
    }

    record Palettes(int[] regions, int[][] colors) {
    }

    private static final class Entry {
        private final int slot;
        private long lastSeen;
        private boolean ready;

        private Entry(int slot, long lastSeen) {
            this.slot = slot;
            this.lastSeen = lastSeen;
        }
    }
}

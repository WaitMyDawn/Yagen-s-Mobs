package yagen.waitmydawn.yagens_mobs.guide.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import yagen.waitmydawn.yagens_mobs.guide.FieldPhotos;

public final class PortraitTextures {
    private static final long started = System.nanoTime();
    private static final Map<String, String> images = new LinkedHashMap<>(32, .75f, true);
    private static final Map<String, Long> requests = new HashMap<>();
    static String get(String key) {
        var image = images.get(key);
        if (image != null)
            return image;
        long now = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (now - requests.getOrDefault(key, -10000L) > 3000) {
            requests.put(key, now);
            FieldPhotos.requestFromClient(key);
        }
        return null;
    }
    public static void accept(String key, byte[] png) {
        if (requests.remove(key) == null || images.containsKey(key))
            return;
        var textures = Minecraft.getInstance().getTextureManager();
        try {
            var image = NativeImage.read(new ByteArrayInputStream(png));
            if (image.getWidth() != 128 || image.getHeight() != 96) {
                image.close();
                return;
            }
            if (images.size() >= 48) {
                var oldest = images.values().iterator();
                textures.release(GuidePlates.id(oldest.next()));
                oldest.remove();
            }
            String path = "runtime/field_guide/" + key;
            GuideUi.upload(path, image);
            images.put(key, path);
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Invalid field guide portrait", e);
        }
    }
    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        images.values().forEach(path -> textures.release(GuidePlates.id(path)));
        images.clear();
        requests.clear();
    }
    private PortraitTextures() {}
}

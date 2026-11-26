package yagen.waitmydawn.yagens_mobs.guide.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import yagen.waitmydawn.yagens_mobs.guide.FieldPhotos;

public final class PortraitTextures {
    private static final Map<String, ResourceLocation> images = new LinkedHashMap<>(32, .75f, true);
    private static final Map<String, Long> requests = new HashMap<>();
    static ResourceLocation get(String key) {
        var image = images.get(key);
        if (image != null)
            return image;
        long now = net.minecraft.Util.getMillis();
        if (now - requests.getOrDefault(key, -10000L) > 3000) {
            requests.put(key, now);
            PacketDistributor.sendToServer(new FieldPhotos.Request(key));
        }
        return null;
    }
    public static void accept(FieldPhotos.Picture p) {
        if (requests.remove(p.key()) == null || images.containsKey(p.key()))
            return;
        var textures = Minecraft.getInstance().getTextureManager();
        try {
            var image = NativeImage.read(new ByteArrayInputStream(p.png()));
            if (image.getWidth() != 128 || image.getHeight() != 96) {
                image.close();
                return;
            }
            if (images.size() >= 48) {
                var oldest = images.values().iterator();
                textures.release(oldest.next());
                oldest.remove();
            }
            var id = GuidePlates.id("runtime/field_guide/" + p.key());
            textures.register(id, new DynamicTexture(image));
            images.put(p.key(), id);
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Invalid field guide portrait", e);
        }
    }
    static void clear() {
        images.values().forEach(Minecraft.getInstance().getTextureManager()::release);
        images.clear();
        requests.clear();
    }
    private PortraitTextures() {}
}

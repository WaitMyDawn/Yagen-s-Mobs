package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;

public final class RenderPose {
    private RenderPose() {}

    public static void rotate(PoseStack stack, Quaternionfc rotation) {
        stack.mulPose(new Matrix4f().rotation(rotation));
    }

    static int minimumBlockLight(int light, int minimum) {
        int block = (light >> 4) & 15;
        return (light & ~(15 << 4)) | (Math.max(block, minimum) << 4);
    }
}

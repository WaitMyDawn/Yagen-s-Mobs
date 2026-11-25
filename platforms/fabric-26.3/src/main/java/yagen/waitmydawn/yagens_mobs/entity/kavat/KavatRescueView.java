package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

public final class KavatRescueView {
    private static final Identifier FACE = Identifier.fromNamespaceAndPath("xaerominimap",
            "entity/icon/sprite/yagens_mobs/kavat_vasca.png");
    private static Player viewer;
    private static int start;

    /** Xaero's resource-defined variantMethod resolves this signature reflectively. */
    public static Object radarVariant(Identifier texture, EntityRenderer<?, ?> renderer, Entity entity) {
        return entity instanceof Kavat cat ? KavatGenes.BREEDS.get(cat.genes().breed()) : "default";
    }
    static void flash() {
        viewer = Minecraft.getInstance().player;
        if (viewer != null) start = viewer.tickCount;
    }
    public static void draw(GuiGraphicsExtractor gui, DeltaTracker delta) {
        if (viewer == null) return;
        if (viewer != Minecraft.getInstance().player || viewer.tickCount - start >= 40) {
            viewer = null;
            return;
        }
        float t = (viewer.tickCount - start + delta.getGameTimeDeltaPartialTick(false)) / 40F;
        float pulse = Mth.sin(Mth.PI * t);
        var stack = gui.pose();
        stack.pushMatrix();
        stack.translate(gui.guiWidth() * .5F + 12 * Mth.sin(2 * Mth.PI * t), gui.guiHeight() * (.52F - .11F * pulse));
        stack.rotate(12 * Mth.DEG_TO_RAD * Mth.sin(2 * Mth.PI * t));
        float size = Math.min(gui.guiHeight() * .42F, 108) * (1 - (float)Math.pow(1 - pulse, 3)) / 18F;
        stack.scale(size, size);
        gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FACE, -9, -9, 23, 23, 18, 18, 64, 64);
        stack.popMatrix();
    }
    private KavatRescueView() {}
}

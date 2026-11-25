package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class IncubatorScreen extends AbstractContainerScreen<IncubatorMenu> {
    public IncubatorScreen(IncubatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = IncubatorMenu.WIDTH;
        imageHeight = IncubatorMenu.HEIGHT;
        titleLabelX = 9;
        titleLabelY = 5;
        inventoryLabelX = 33;
        inventoryLabelY = 123;
    }
    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff222c32);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xffccd2ca);
        g.fill(x + 4, y + 16, x + imageWidth - 4, y + 69, 0xffaebcb7);
        g.fill(x + 4, y + 72, x + imageWidth - 4, y + 122, 0xffb8c6bf);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.getSlot(i);
            if (slot.x < 0 || (i == 21 || i == 22 || i == 24 || i == 26) && !slot.hasItem())
                continue;
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xfff3f3e8);
            g.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xff536669);
            g.fill(sx, sy, sx + 16, sy + 16, i < 36 ? 0xff92a29e : 0xff8c9793);
        }
    }
    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, Component.translatable("kavat.ui." + key), x, y, 0xff233f41, false);
    }
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        label(g, "samples", 9, 18);
        label(g, "select", 128, 33);
        int seconds = menu.remainingSeconds.get();
        if (seconds < 0)
            label(g, "start", 128, 53);
        else
            g.drawString(font, String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), 128, 53,
                    0xff233f41, false);
        label(g, "pets", 30, 82);
        label(g, "memories", 128, 82);
    }
    @Override
    public void render(GuiGraphics g, int x, int y, float tick) {
        super.render(g, x, y, tick);
        renderTooltip(g, x, y);
    }
}

package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class IncubatorScreen extends AbstractContainerScreen<IncubatorMenu> {
    private static final int INK = 0xff233f41, PANEL = 0xffb7c6be;
    private Button start, archive, back, previous, next, revive, delete;
    private Kavat preview;
    private int lastRevision = -1;
    private Component petName;
    private KavatGenes genes;

    public IncubatorScreen(IncubatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 226;
        imageHeight = 188;
        inventoryLabelX = 31;
        inventoryLabelY = 92;
    }

    private static Component ui(String key, Object... args) { return Component.translatable("kavat.ui." + key, args); }

    private Button button(Component label, int x, int y, int width, int operation) {
        return addRenderableWidget(Button.builder(label, b -> minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, menu.action(operation))).bounds(leftPos + x, topPos + y, width, 18).build());
    }

    @Override protected void init() {
        super.init();
        lastRevision = -1;
        start = button(ui("start"), 112, 30, 103, IncubatorMenu.START);
        archive = button(ui("memories"), 112, 54, 103, IncubatorMenu.ARCHIVE);
        back = button(Component.translatable("gui.back"), 163, 4, 54, IncubatorMenu.BACK);
        previous = button(Component.literal("◀"), 16, 148, 23, IncubatorMenu.PREVIOUS);
        next = button(Component.literal("▶"), 76, 148, 23, IncubatorMenu.NEXT);
        previous.setTooltip(Tooltip.create(Component.translatable("kavat.previous")));
        next.setTooltip(Tooltip.create(Component.translatable("kavat.next")));
        revive = button(ui("revive"), 108, 136, 106, IncubatorMenu.REVIVE);
        delete = button(ui("delete"), 108, 158, 106, IncubatorMenu.DELETE);
    }

    private void widgets() {
        boolean browsing = menu.archive, hasPet = browsing && menu.count > 0;
        boolean idle = menu.remainingSeconds.get() < 0;
        archive.visible = !browsing;
        archive.active = menu.getCarried().isEmpty();
        start.visible = !browsing && idle;
        start.active = menu.ready() && menu.getCarried().isEmpty();
        back.visible = browsing;
        previous.visible = next.visible = revive.visible = delete.visible = hasPet;
        previous.active = menu.page > 0;
        next.active = menu.page + 1 < menu.count;
        if (menu.revision != lastRevision) updateRecord(hasPet);
        revive.active = hasPet && idle && minecraft.player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(KavatSample.MATERIALS.get(genes.coat())));
    }

    private void updateRecord(boolean hasPet) {
        lastRevision = menu.revision;
        delete.setMessage(ui(menu.confirming ? "confirm" : "delete"));
        preview = null;
        if (!hasPet) return;
        genes = KavatGenes.unpack(menu.detail.getInt("KavatTraits"));
        preview = ModEntities.KAVAT.get().create(minecraft.level);
        if (preview != null) preview.phenotype(genes, menu.detail.getInt("Generation"));
        petName = menu.detail.contains("CustomName")
                ? Component.Serializer.fromJson(menu.detail.getString("CustomName"), minecraft.level.registryAccess()) : null;
        if (petName == null) petName = Component.translatable("entity.yagens_mobs.kavat");
        var material = KavatSample.MATERIALS.get(genes.coat());
        revive.setTooltip(Tooltip.create(Component.translatable("kavat.cost", material.getDescription())));

    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff384a4b);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xffd4d9cf);
        if (menu.archive) {
            g.fill(x + 8, y + 29, x + 104, y + 143, PANEL);
            if (preview != null)
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 9, y + 30, x + 103, y + 142,
                        44, .10f, x + 24, y + 91, preview);
            return;
        }
        g.fill(x + 6, y + 19, x + 220, y + 82, PANEL);
        for (var slot : menu.slots) {
            if (!slot.isActive()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xfff0f2e8);
            g.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xff4e6263);
            g.fill(sx, sy, sx + 16, sy + 16, 0xff91a09b);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (!menu.archive) {
            super.renderLabels(g, mx, my);
            g.drawString(font, ui("samples"), 12, 23, INK, false);
            int seconds = menu.remainingSeconds.get();
            if (seconds >= 0) {
                double progress = 1 - seconds / 300.0;
                for (int y = -9; y <= 9; y++) for (int x = -9; x <= 9; x++) {
                    int r = x*x + y*y;
                    if (r > 81 || r < 43) continue;
                    double angle = Math.atan2(x, -y) / (Math.PI * 2);
                    if (angle < 0) angle++;
                    g.fill(123+x, 38+y, 124+x, 39+y, angle < progress ? 0xff18796c : 0xff80958f);
                }
                Component text = seconds == 0 ? ui("blocked") : Component.literal(
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
                g.drawString(font, text, 137, 34, INK, false);
            }
            return;
        }
        g.drawString(font, ui("memories"), 10, 9, INK, false);
        if (menu.count == 0) {
            g.drawCenteredString(font, ui("empty"), imageWidth / 2, 86, 0xffffff);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(petName.getString(), 108), 109, 33, INK, false);
        g.drawString(font, KavatSample.trait(KavatGenes.BREEDS.get(genes.breed())), 109, 50, INK, false);
        g.drawString(font, KavatSample.trait(KavatGenes.COATS.get(genes.coat())), 109, 62, INK, false);
        g.drawString(font, KavatSample.trait(KavatGenes.EARS.get(genes.ear())), 109, 74, INK, false);
        g.drawString(font, KavatSample.trait(KavatGenes.TAILS.get(genes.tail())), 109, 86, INK, false);
        g.drawString(font, ui("lineage", menu.detail.getInt("Generation"),
                Math.clamp(2 - menu.detail.getInt("TemplatesMade"), 0, 2)), 109, 102, INK, false);
        var cost = KavatSample.MATERIALS.get(genes.coat()).getDefaultInstance();
        g.renderItem(cost, 109, 114);
        g.renderItemDecorations(font, cost, 109, 114, "1");
        g.drawString(font, cost.getHoverName(), 129, 118, INK, false);
        g.drawCenteredString(font, (menu.page + 1) + "/" + menu.count, 57, 154, 0xffffff);
    }

    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        widgets();
        super.render(g, x, y, tick);
        renderTooltip(g, x, y);
        if (menu.archive && menu.count > 0 && isHovering(109, 31, 108, 13, x, y)) g.renderTooltip(font, petName, x, y);
    }
}

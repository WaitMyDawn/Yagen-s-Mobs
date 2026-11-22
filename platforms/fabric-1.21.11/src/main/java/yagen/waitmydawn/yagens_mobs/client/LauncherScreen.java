package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.LauncherMenu;

final class LauncherScreen extends AbstractContainerScreen<LauncherMenu> {
    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier SILHOUETTE =
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "textures/gui/spool_slot.png");

    LauncherScreen(LauncherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 147;
        inventoryLabelY = 54;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos, 0, 0, imageWidth, 53, 256, 256);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos + 53, 0, 126, imageWidth, 94, 256, 256);
        graphics.fill(leftPos + 6, topPos + 17, leftPos + imageWidth - 6, topPos + imageHeight - 5, 0xffc6c6c6);
        for (var slot : menu.slots) {
            drawSlot(graphics, leftPos + slot.x, topPos + slot.y, slot.index == 0);
        }

        var extension = menu.getSlot(0);
        if (!extension.hasItem()) {
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SILHOUETTE, leftPos + extension.x, topPos + extension.y, 0, 0, 16, 16, 16, 16);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, boolean extension) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, extension ? 0xff6e593b : 0xff555555);
        graphics.fill(x, y, x + 16, y + 16, extension ? 0xffa48c63 : 0xff8b8b8b);
        graphics.fill(x, y + 16, x + 17, y + 17, 0xffffffff);
        graphics.fill(x + 16, y, x + 17, y + 16, 0xffffffff);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
        var extension = menu.getSlot(0);
        if (!extension.hasItem() && hoveredSlot == extension) {
            graphics.setTooltipForNextFrame(font, Component.translatable("screen.yagens_mobs.silk_launcher.extension"), mouseX, mouseY);
        }
    }
}


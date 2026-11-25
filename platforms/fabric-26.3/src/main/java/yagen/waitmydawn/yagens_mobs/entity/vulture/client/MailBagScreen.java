package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;

public final class MailBagScreen extends AbstractContainerScreen<MailBagMenu> {
    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button send;

    public MailBagScreen(MailBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        send = addRenderableWidget(Button.builder(Component.translatable("screen.yagens_mobs.mail_bag.send"),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MailBagMenu.SEND))
                .bounds(leftPos + 111, topPos + 3, 59, 14).build());
        send.active = send.visible = menu.canSend();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        send.active = send.visible = menu.canSend();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        extractTooltip(graphics, mouseX, mouseY);
        Component destination = menu.boundLabel();
        if (destination != null && mouseX >= leftPos + titleLabelX && mouseX < leftPos + titleWidth()
                && mouseY >= topPos + 3 && mouseY < topPos + 17) {
            graphics.setTooltipForNextFrame(font, destination, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component destination = menu.boundLabel();
        Component heading = destination == null ? title
                : Component.translatable("screen.yagens_mobs.mail_bag.bound_title", destination);
        graphics.text(font, font.plainSubstrByWidth(heading.getString(), titleWidth() - titleLabelX),
                titleLabelX, titleLabelY, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    private int titleWidth() {
        return menu.canSend() ? 107 : imageWidth - 8;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos, 0, 0, imageWidth, 71, 256, 256);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos + 71, 0, 126, imageWidth, 96, 256, 256);
    }
}

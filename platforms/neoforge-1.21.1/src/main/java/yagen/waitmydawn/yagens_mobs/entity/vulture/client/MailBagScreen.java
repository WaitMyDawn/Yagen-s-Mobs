package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;

public final class MailBagScreen extends AbstractContainerScreen<MailBagMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        send.active = send.visible = menu.canSend();
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
        Component destination = menu.boundLabel();
        if (destination != null && mouseX >= leftPos + titleLabelX && mouseX < leftPos + titleWidth()
                && mouseY >= topPos + 3 && mouseY < topPos + 17) {
            graphics.renderTooltip(font, destination, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component destination = menu.boundLabel();
        Component heading = destination == null ? title
                : Component.translatable("screen.yagens_mobs.mail_bag.bound_title", destination);
        graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), titleWidth() - titleLabelX),
                titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    private int titleWidth() {
        return menu.canSend() ? 107 : imageWidth - 8;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, 71);
        graphics.blit(CHEST, leftPos, topPos + 71, 0, 126, imageWidth, 96);
    }
}

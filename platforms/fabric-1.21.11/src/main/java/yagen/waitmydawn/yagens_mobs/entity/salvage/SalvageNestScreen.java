package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

final class SalvageNestScreen extends AbstractContainerScreen<SalvageNestMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Component RENAME = Component.translatable("screen.yagens_mobs.salvage.rename");
    private static final Tooltip RENAME_HINT = Tooltip.create(RENAME);

    private final SalvageNestMenu nest;
    private Component name;
    private PlainTextButton titleButton;
    private EditBox editor;

    private SalvageNestScreen(SalvageNestMenu menu, Inventory inventory, Component name) {
        super(menu, inventory, name);
        imageHeight = 168;
        inventoryLabelY = imageHeight - 94;
        this.nest = menu;
        this.name = name;
    }

    public static void register() {
        net.minecraft.client.gui.screens.MenuScreens.register(SalvageNestMenu.TYPE.get(), SalvageNestScreen::new);
    }

    static void renamed(CompoundTag data) {
        if (Minecraft.getInstance().screen instanceof SalvageNestScreen screen
                && screen.nest.containerId == data.getIntOr("Menu", 0)) {
            String text = data.getStringOr("Name", "");
            screen.name = text.isEmpty() ? defaultName() : Component.literal(text);
            screen.addTitleButton();
        }
    }

    @Override
    protected void init() {
        super.init();
        editor = null;
        titleButton = null;
        addTitleButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateRenamePermission();
    }

    private static Component defaultName() {
        return SalvageRays.NEST_BOX.get().getName();
    }

    private void addTitleButton() {
        boolean wasFocused = titleButton != null && getFocused() == titleButton;
        if (titleButton != null) {
            removeWidget(titleButton);
        }
        int labelWidth = imageWidth - titleLabelX - 8;
        String heading = name.getString();
        String visibleName = font.plainSubstrByWidth(heading.isBlank() ? defaultName().getString() : heading, labelWidth);
        titleButton = addRenderableWidget(new PlainTextButton(leftPos + titleLabelX, topPos + titleLabelY, labelWidth,
                font.lineHeight + 2, Component.literal(visibleName), button -> editName(), font) {
            @Override
            public void renderContents(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
                int color = active && isHoveredOrFocused() ? 0xFF55337B : 0xFF303030;
                gui.drawString(SalvageNestScreen.this.font, getMessage(), getX(), getY(), color, false);
            }

            @Override
            public boolean shouldTakeFocusAfterInteraction() {
                // Activation transfers focus to the inline editor.
                return false;
            }
        });
        titleButton.visible = editor == null;
        updateRenamePermission();
        if (wasFocused) {
            setFocused(titleButton.active ? titleButton : null);
        }
    }

    private void updateRenamePermission() {
        boolean canRename = nest.canRename;
        titleButton.active = canRename;
        titleButton.setTooltip(canRename ? RENAME_HINT : null);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partial, int mouseX, int mouseY) {
        gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 71, 256, 256);
        gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + 71, 0, 126, imageWidth, 96, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        renderTooltip(gui, mouseX, mouseY);
    }

    private void editName() {
        if (editor != null || !titleButton.active) return;
        titleButton.visible = false;
        editor = new EditBox(font, leftPos + 7, topPos + 3, imageWidth - 14, 13, RENAME);
        editor.setMaxLength(64);
        editor.setTextShadow(false);
        editor.setValue(name.equals(defaultName()) ? "" : name.getString());
        addRenderableWidget(editor);
        setFocused(editor);
        editor.setFocused(true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editor != null) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                var data = SalvageRoutes.action("Rename");
                data.putInt("Menu", nest.containerId);
                data.putString("Name", editor.getValue());
                ClientPlayNetworking.send(new SalvageRoutes.Request(data));
            } else if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
                editor.keyPressed(event);
                return true;
            }
            removeWidget(editor);
            editor = null;
            titleButton.visible = true;
            setFocused(titleButton);
            return true;
        }
        return super.keyPressed(event);
    }
}

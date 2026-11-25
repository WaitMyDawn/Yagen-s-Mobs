package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
final class SalvageNestScreen extends AbstractContainerScreen<SalvageNestMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final SalvageNestMenu nest;
    private Component name;
    private EditBox editor;

    private SalvageNestScreen(SalvageNestMenu menu, Inventory inventory, Component name) {
        super(menu, inventory, name);
        imageHeight = 168;
        inventoryLabelY = imageHeight - 94;
        this.nest = menu;
        this.name = name;
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(SalvageNestMenu.TYPE.get(), SalvageNestScreen::new);
    }

    static void renamed(CompoundTag data) {
        if (Minecraft.getInstance().screen instanceof SalvageNestScreen screen
                && screen.nest.containerId == data.getInt("Menu")) {
            String text = data.getString("Name");
            screen.name = text.isEmpty() ? Component.translatable("block.yagens_mobs.salvage_nest_box") : Component.literal(text);
        }
    }

    @Override
    protected void init() {
        super.init();
        editor = null;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partial, int mouseX, int mouseY) {
        gui.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 71);
        gui.blit(BACKGROUND, leftPos, topPos + 71, 0, 126, imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        if (editor == null) {
            gui.drawString(font, font.plainSubstrByWidth(name.getString(), imageWidth - 18), titleLabelX, titleLabelY,
                    hoverName(mouseX, mouseY) && nest.canRename ? 0x55337b : 0x404040, false);
        }
        gui.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
        renderTooltip(gui, mouseX, mouseY);
        if (editor == null && nest.canRename && hoverName(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("screen.yagens_mobs.salvage.rename"), mouseX, mouseY);
        }
    }

    private boolean hoverName(double x, double y) {
        return x >= leftPos + titleLabelX && x < leftPos + imageWidth - 8
                && y >= topPos + 3 && y < topPos + 17;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && nest.canRename && editor == null && hoverName(x, y)) {
            editor = new EditBox(font, leftPos + 7, topPos + 3, imageWidth - 14, 13,
                    Component.translatable("screen.yagens_mobs.salvage.rename"));
            editor.setMaxLength(64);
            editor.setValue(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents ? "" : name.getString());
            addRenderableWidget(editor);
            setFocused(editor);
            editor.setFocused(true);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editor != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                var data = SalvageRoutes.action("Rename");
                data.putInt("Menu", nest.containerId);
                data.putString("Name", editor.getValue());
                PacketDistributor.sendToServer(new SalvageRoutes.Request(data));
            } else if (key != GLFW.GLFW_KEY_ESCAPE) {
                editor.keyPressed(key, scan, modifiers);
                return true;
            }
            removeWidget(editor);
            editor = null;
            setFocused(null);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}

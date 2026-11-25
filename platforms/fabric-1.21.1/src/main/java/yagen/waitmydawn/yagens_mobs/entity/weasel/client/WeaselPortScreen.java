package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import yagen.waitmydawn.yagens_mobs.entity.weasel.mixin.WeaselScreenBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.WeaselPortPackets;

public final class WeaselPortScreen extends Screen {
    private record Settings(UUID session, int menu, int weasel, boolean grid, String mode, int priority) {}

    private static @Nullable Settings settings;
    private static @Nullable Controls embedded;
    private static @Nullable Screen embeddedIn;

    private @Nullable Controls own;

    private WeaselPortScreen() {
        super(text("title"));
    }

    public static void open(CompoundTag tag) {
        if (!tag.hasUUID("Session")) return;
        settings = new Settings(tag.getUUID("Session"), tag.getInt("Menu"), tag.getInt("Weasel"),
                tag.getBoolean("Grid"), tag.getString("Mode"), tag.getInt("Priority"));
        var minecraft = Minecraft.getInstance();
        if (settings.menu() == -1) {
            if (!(minecraft.screen instanceof WeaselPortScreen)) minecraft.setScreen(new WeaselPortScreen());
        } else if (embedded != null && embeddedIn == minecraft.screen) {
            embedded.refresh();
        }
    }

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, current, width, height) -> {
            if (!(current instanceof AbstractContainerScreen<?> screen)
                    || !WeaselPortPackets.isNativeMenu(screen.getMenu())) return;
            var bounds = (WeaselScreenBounds) screen;
            int x = bounds.yagensMobs$left() + 2;
            int y = bounds.yagensMobs$top() - 22;
            if (y < 2) y = bounds.yagensMobs$top() + bounds.yagensMobs$height() + 2;
            if (y + 20 > height) {
                x = Math.max(2, bounds.yagensMobs$left() - Controls.WIDTH - 2);
                y = 2;
            }
            embeddedIn = screen;
            embedded = new Controls(screen, x, y);
            Screens.getButtons(screen).add(embedded.button);
            Screens.getButtons(screen).add(embedded.input);
            Screens.getButtons(screen).add(embedded.apply);
            embedded.refresh();
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> !key(s, key));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickControls());
    }

    private static void tickControls() {
        var client = Minecraft.getInstance();
        if (embeddedIn != null && embeddedIn != client.screen) {
            if (settings != null && settings.menu() != -1) settings = null;
            embedded = null;
            embeddedIn = null;
        }
        if (embedded != null) embedded.refresh();
        if (client.screen instanceof WeaselPortScreen screen && screen.own != null) {
            if (!carried()) screen.onClose();
            else screen.own.refresh();
        }
    }

    private static boolean key(Screen screen, int keyCode) {
        var controls = screen == embeddedIn ? embedded : null;
        if (controls == null || !controls.input.isFocused() || !controls.input.visible) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            controls.commit();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            controls.edit(false);
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        own = new Controls(this, (width - Controls.WIDTH) / 2, height / 2 + 5);
        addRenderableWidget(own.button);
        addRenderableWidget(own.input);
        addRenderableWidget(own.apply);
        own.refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        super.render(graphics, x, y, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 24, 0xFFFFFF);
        if (settings != null) graphics.drawCenteredString(font, mode(), width / 2, height / 2 - 10, 0xD0D0D0);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        settings = null;
        super.onClose();
    }

    private static boolean carried() {
        var player = Minecraft.getInstance().player;
        return settings != null && player != null && player.getPassengers().stream()
                .anyMatch(mob -> mob.getId() == settings.weasel() && mob.isAlive());
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.yagens_mobs.weasel_storage." + key, args);
    }

    private static Component mode() {
        return text("mode." + settings.mode());
    }

    private static void send(int priority) {
        if (settings == null || !carried()) return;
        var tag = new CompoundTag();
        tag.putUUID("Session", settings.session());
        if (!settings.grid()) tag.putInt("Priority", priority);
        tag.putBoolean("Storage", true);
        WeaselClient.request(tag);
    }

    private static final class Controls {
        static final int WIDTH = 122;
        private final Screen screen;
        private final Button button;
        private final EditBox input;
        private final Button apply;

        private Controls(Screen screen, int x, int y) {
            this.screen = screen;
            input = new EditBox(Minecraft.getInstance().font, x, y, WIDTH - 25, 20, text("priority_label"));
            input.setMaxLength(3);
            input.setFilter(value -> value.chars().allMatch(character -> character >= '0' && character <= '9'));
            button = Button.builder(Component.empty(), widget -> {
                if (settings == null) return;
                if (settings.grid()) send(0);
                else {
                    input.setValue(Integer.toString(settings.priority()));
                    edit(true);
                }
            }).bounds(x, y, WIDTH, 20).build();
            apply = Button.builder(Component.literal("\u2713"), widget -> commit()).bounds(x + WIDTH - 22, y, 22, 20).build();
            apply.setTooltip(Tooltip.create(text("apply")));
            input.visible = false;
            apply.visible = false;
        }

        private void refresh() {
            if (!carried() || screen instanceof AbstractContainerScreen<?> container
                    && settings.menu() != container.getMenu().containerId) {
                edit(false);
                button.visible = false;
                return;
            }
            button.visible = !input.visible;
            var label = settings.grid() ? mode() : text("priority", settings.priority());
            var tooltip = settings.grid() ? text("grid_hint") : text("priority_hint", mode());
            if (!button.getMessage().equals(label)) {
                button.setMessage(label);
                button.setTooltip(Tooltip.create(tooltip));
            }
            apply.active = !input.getValue().isEmpty();
        }

        private void edit(boolean active) {
            input.visible = active;
            apply.visible = active;
            button.visible = !active;
            if (active) {
                screen.setFocused(input);
                input.setFocused(true);
                input.setHighlightPos(0);
            } else if (screen.getFocused() == input) {
                screen.setFocused(null);
                input.setFocused(false);
            }
        }

        private void commit() {
            if (input.getValue().isEmpty()) return;
            send(Integer.parseInt(input.getValue()));
            edit(false);
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationPackets;

public final class VultureStationScreen extends Screen {
    private static final String[] DIRECTIONS = {"south", "southeast", "east", "northeast", "north", "northwest", "west", "southwest"};
    private final long origin;
    private CompoundTag data;
    @Nullable private EditBox name;
    @Nullable private PlainTextButton titleButton;
    private int refreshTicks;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;

    private VultureStationScreen(CompoundTag snapshot) {
        super(tr("title"));
        origin = snapshot.getLong("Origin");
        data = snapshot;
    }

    public static void accept(StationPackets.Snapshot payload) {
        var game = Minecraft.getInstance();
        var snapshot = payload.data();
        if (snapshot.getBoolean("Open")) {
            game.setScreen(new VultureStationScreen(snapshot));
        } else if (game.screen instanceof VultureStationScreen current && current.origin == snapshot.getLong("Origin")) {
            current.refresh(snapshot);
        }
    }

    private void refresh(CompoundTag snapshot) {
        CompoundTag previous = data.copy();
        previous.remove("Open");
        snapshot.remove("Open");
        if (snapshot.equals(previous)) {
            return;
        }
        data = snapshot;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        EditBox previousEditor = name;
        name = null;
        titleButton = null;
        panelWidth = Math.min(width - 16, 424);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(8, (height - 220) / 2);
        int x = panelLeft + 9;
        int y = panelTop + 44;
        if (data.getBoolean("Owner")) {
            if (data.getInt("Mail") > 0) {
                button(tr("take_mail", data.getInt("Mail")), panelLeft + panelWidth - 120, panelTop + 5, 111,
                        ignored -> send(request("take_mail", data.getInt("Page"))));
            }
            var title = Component.literal(visibleTitle());
            titleButton = addRenderableWidget(new PlainTextButton(x + 1, panelTop + 10,
                    Math.max(8, font.width(title)), font.lineHeight + 2, title, ignored -> editName(data.getString("Name")), font) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    int color = isHoveredOrFocused() ? 0xFFFFC986 : 0xFFF5E7D2;
                    graphics.drawString(VultureStationScreen.this.font, getMessage(), getX(), getY(), color, false);
                }
            });
            titleButton.setTooltip(Tooltip.create(tr("rename")));
            if (previousEditor != null) {
                editName(previousEditor.getValue());
                name.setCursorPosition(previousEditor.getCursorPosition());
            }
            boolean publicListing = data.getBoolean("Public");
            boolean forwardHome = data.getBoolean("Return");
            int half = (panelWidth - 21) / 2;
            button(tr(publicListing ? "public" : "private"), x, y, half,
                    ignored -> setOption("Public", !publicListing));
            button(tr(forwardHome ? "return" : "stay"), x + half + 3, y, half,
                    ignored -> setOption("Return", !forwardHome));
            y += 24;
        }
        for (Tag value : data.getList("Rows", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            var label = nameOf(row);
            var direction = tr(DIRECTIONS[row.getInt("Direction")]);
            var summary = tr("row", label, tr(row.getBoolean("Yours") ? "mine" : "other"),
                    direction, row.getInt("Distance"), row.getInt("Free"), row.getInt("Total"), row.getInt("Cost"));
            var entry = button(summary, x, y, panelWidth - 18, clicked -> {
                var request = request("deploy", data.getInt("Page"));
                request.putLong("Target", row.getLong("Pos"));
                send(request);
            });
            entry.active = row.getBoolean("Enabled");
            List<Component> detail = new ArrayList<>();
            detail.add(summary);
            for (Tag reserved : row.getList("Reservations", Tag.TAG_COMPOUND)) {
                CompoundTag slot = (CompoundTag) reserved;
                var sender = Component.Serializer.fromJson(slot.getString("Label"), Minecraft.getInstance().level.registryAccess());
                if (sender != null) {
                    detail.add(tr("reserved", sender, slot.getInt("Count")));
                }
            }
            if (!entry.active) {
                detail.add(tr("not_ready"));
            }
            var message = Component.empty();
            for (Component line : detail) {
                if (!message.getSiblings().isEmpty()) {
                    message.append("\n");
                }
                message.append(line);
            }
            entry.setTooltip(Tooltip.create(message));
            y += 22;
        }
        int bottom = Math.max(y + 3, panelTop + 211);
        int current = data.getInt("Page");
        int pages = data.getInt("Pages");
        button(Component.literal("‹"), x, bottom, 24, ignored -> send(request("refresh", current - 1))).active = current > 0;
        button(Component.literal("›"), x + 27, bottom, 24, ignored -> send(request("refresh", current + 1))).active = current + 1 < pages;
        button(tr("refresh"), x + 56, bottom, 58, ignored -> send(request("refresh", current)));
        if (data.getBoolean("Owner")) {
            button(tr("withdraw"), x + 118, bottom, 90, ignored -> send(request("withdraw", current))).active = data.getInt("Fuel") > 0;
            button(tr("deposit"), x + 212, bottom, 115, ignored -> send(request("deposit", current))).active =
                    data.getInt("Fuel") < data.getInt("FuelCapacity");
        }
    }

    private int titleWidth() {
        return panelWidth - (data.getInt("Mail") > 0 ? 135 : 20);
    }

    private String visibleTitle() {
        return font.plainSubstrByWidth(nameOf(data).getString(), titleWidth());
    }

    private void editName(String value) {
        if (name != null || titleButton == null) {
            return;
        }
        titleButton.visible = false;
        name = addRenderableWidget(new EditBox(font, panelLeft + 9, panelTop + 5, titleWidth(), 19, tr("name")));
        name.setMaxLength(40);
        name.setHint(tr("name"));
        name.setValue(value);
        setInitialFocus(name);
    }

    private void endRename() {
        removeWidget(name);
        name = null;
        titleButton.visible = true;
        setInitialFocus(titleButton);
    }

    private void setOption(String option, boolean enabled) {
        var values = request("configure", data.getInt("Page"));
        values.putBoolean(option, enabled);
        send(values);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasEditing = name != null;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!wasEditing && name != null) {
            setInitialFocus(name);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (name != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                var values = request("configure", data.getInt("Page"));
                values.putString("Name", name.getValue());
                endRename();
                send(values);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                endRename();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void tick() {
        if (++refreshTicks % 30 == 0) {
            send(request("refresh", data.getInt("Page")));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, Math.min(height - 3, panelTop + 236), 0xE92D241D);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFFD18856);
        if (titleButton == null) {
            graphics.drawString(font, visibleTitle(), panelLeft + 10, panelTop + 10, 0xFFF5E7D2, false);
        }
        graphics.drawString(font, tr("summary", data.getInt("Cargo"), data.getInt("Birds"), data.getInt("Fuel"), data.getInt("FuelCapacity")),
                panelLeft + 10, panelTop + 26, 0xFFDFC4AA);
        if (data.getList("Rows", Tag.TAG_COMPOUND).isEmpty()) {
            graphics.drawString(font, tr("empty"), panelLeft + 10, panelTop + 101, 0xFFDFC4AA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component nameOf(CompoundTag data) {
        String value = data.getString("Name");
        return value.isBlank() ? tr("title") : Component.literal(value);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.yagens_mobs.vulture_station." + key, args);
    }

    private Button button(Component label, int x, int y, int width, Button.OnPress click) {
        return addRenderableWidget(Button.builder(label, click).bounds(x, y, width, 20).build());
    }

    private CompoundTag request(String action, int page) {
        var result = new CompoundTag();
        result.putLong("Origin", origin);
        result.putInt("Page", page);
        result.putString("Action", action);
        return result;
    }

    private static void send(CompoundTag request) {
        ClientPlayNetworking.send(new StationPackets.Request(request));
    }
}

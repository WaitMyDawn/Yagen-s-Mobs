package yagen.waitmydawn.yagens_mobs.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;

public final class VultureStationScreen extends Screen {
    private static final String[] DIRECTIONS = {"south", "southeast", "east", "northeast", "north", "northwest", "west", "southwest"};
    private final long origin;
    private CompoundTag data;
    @Nullable private EditBox name;
    private boolean forwardHome;
    private boolean publicListing;
    private boolean editing;
    private int refreshTicks;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;

    private VultureStationScreen(CompoundTag snapshot) {
        super(tr("title"));
        origin = snapshot.getLong("Origin");
        data = snapshot;
        forwardHome = data.getBoolean("Return");
        publicListing = data.getBoolean("Public");
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
        String draft = editing && name != null ? name.getValue() : snapshot.getString("Name");
        boolean focused = name != null && name.isFocused();
        data = snapshot;
        if (!editing) {
            forwardHome = data.getBoolean("Return");
            publicListing = data.getBoolean("Public");
        }
        rebuildWidgets();
        if (name != null) {
            name.setResponder(value -> {});
            name.setValue(draft);
            name.setResponder(value -> editing = true);
            if (focused) {
                setFocused(name);
            }
        }
    }

    @Override
    protected void init() {
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
            name = addRenderableWidget(new EditBox(font, x, y, panelWidth - 87, 20, tr("name")));
            name.setMaxLength(40);
            name.setHint(tr("name"));
            name.setValue(data.getString("Name"));
            name.setResponder(value -> editing = true);
            button(tr("save"), panelLeft + panelWidth - 73, y, 64, ignored -> saveSettings());
            y += 23;
            int half = (panelWidth - 21) / 2;
            button(tr(publicListing ? "public" : "private"), x, y, half, which -> {
                publicListing = !publicListing;
                editing = true;
                which.setMessage(tr(publicListing ? "public" : "private"));
            });
            button(tr(forwardHome ? "return" : "stay"), x + half + 3, y, half, which -> {
                forwardHome = !forwardHome;
                editing = true;
                which.setMessage(tr(forwardHome ? "return" : "stay"));
            });
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
                var sender = Component.Serializer.fromJson(slot.getString("Label"));
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

    private void saveSettings() {
        if (name != null) {
            var values = request("configure", data.getInt("Page"));
            values.putString("Name", name.getValue());
            values.putBoolean("Public", publicListing);
            values.putBoolean("Return", forwardHome);
            editing = false;
            send(values);
        }
    }

    @Override
    public void tick() {
        if (++refreshTicks % 30 == 0) {
            send(request("refresh", data.getInt("Page")));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, Math.min(height - 3, panelTop + 236), 0xE92D241D);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFFD18856);
        int titleWidth = panelWidth - (data.getInt("Mail") > 0 ? 135 : 20);
        graphics.drawString(font, font.plainSubstrByWidth(nameOf(data).getString(), titleWidth),
                panelLeft + 10, panelTop + 10, 0xFFF5E7D2);
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
        LegacyNetwork.sendToServer(new StationPackets.Request(request));
    }
}

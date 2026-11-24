package yagen.waitmydawn.yagens_mobs.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;

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
        origin = snapshot.getLongOr("Origin", 0L);
        data = snapshot;
    }

    public static void accept(StationPackets.Snapshot payload) {
        var game = Minecraft.getInstance();
        var snapshot = payload.data();
        if (snapshot.getBooleanOr("Open", false)) {
            game.gui.setScreen(new VultureStationScreen(snapshot));
        } else if (game.gui.screen() instanceof VultureStationScreen current && current.origin == snapshot.getLongOr("Origin", 0L)) {
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
        if (data.getBooleanOr("Owner", false)) {
            if (data.getIntOr("Mail", 0) > 0) {
                button(tr("take_mail", data.getIntOr("Mail", 0)), panelLeft + panelWidth - 120, panelTop + 5, 111,
                        ignored -> send(request("take_mail", data.getIntOr("Page", 0))));
            }
            var title = Component.literal(visibleTitle());
            titleButton = addRenderableWidget(new PlainTextButton(x + 1, panelTop + 10,
                    Math.max(8, font.width(title)), font.lineHeight + 2, title, ignored -> editName(data.getStringOr("Name", "")), font) {
                @Override
                public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    int color = isHoveredOrFocused() ? 0xFFFFC986 : 0xFFF5E7D2;
                    graphics.text(VultureStationScreen.this.font, getMessage(), getX(), getY(), color, false);
                }

                @Override
                public boolean shouldTakeFocusAfterInteraction() {
                    return false;
                }
            });
            titleButton.setTooltip(Tooltip.create(tr("rename")));
            if (previousEditor != null) {
                editName(previousEditor.getValue());
                name.setCursorPosition(previousEditor.getCursorPosition());
            }
            boolean publicListing = data.getBooleanOr("Public", false);
            boolean forwardHome = data.getBooleanOr("Return", false);
            int half = (panelWidth - 21) / 2;
            button(tr(publicListing ? "public" : "private"), x, y, half,
                    ignored -> setOption("Public", !publicListing));
            button(tr(forwardHome ? "return" : "stay"), x + half + 3, y, half,
                    ignored -> setOption("Return", !forwardHome));
            y += 24;
        }
        for (Tag value : data.getListOrEmpty("Rows")) {
            var row = (CompoundTag) value;
            var label = nameOf(row);
            var direction = tr(DIRECTIONS[row.getIntOr("Direction", 0)]);
            var summary = tr("row", label, tr(row.getBooleanOr("Yours", false) ? "mine" : "other"),
                    direction, row.getIntOr("Distance", 0), row.getIntOr("Free", 0), row.getIntOr("Total", 0), row.getIntOr("Cost", 0));
            var entry = button(summary, x, y, panelWidth - 18, clicked -> {
                var request = request("deploy", data.getIntOr("Page", 0));
                request.putLong("Target", row.getLongOr("Pos", 0L));
                send(request);
            });
            entry.active = row.getBooleanOr("Enabled", false);
            List<Component> detail = new ArrayList<>();
            detail.add(summary);
            for (Tag reserved : row.getListOrEmpty("Reservations")) {
                CompoundTag slot = (CompoundTag) reserved;
                var sender = slot.read("Label", net.minecraft.network.chat.ComponentSerialization.CODEC,
                        Minecraft.getInstance().level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)).orElse(null);
                if (sender != null) {
                    detail.add(tr("reserved", sender, slot.getIntOr("Count", 0)));
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
        int current = data.getIntOr("Page", 0);
        int pages = data.getIntOr("Pages", 0);
        button(Component.literal("‹"), x, bottom, 24, ignored -> send(request("refresh", current - 1))).active = current > 0;
        button(Component.literal("›"), x + 27, bottom, 24, ignored -> send(request("refresh", current + 1))).active = current + 1 < pages;
        button(tr("refresh"), x + 56, bottom, 58, ignored -> send(request("refresh", current)));
        if (data.getBooleanOr("Owner", false)) {
            button(tr("withdraw"), x + 118, bottom, 90, ignored -> send(request("withdraw", current))).active = data.getIntOr("Fuel", 0) > 0;
            button(tr("deposit"), x + 212, bottom, 115, ignored -> send(request("deposit", current))).active =
                    data.getIntOr("Fuel", 0) < data.getIntOr("FuelCapacity", 0);
        }
    }

    private int titleWidth() {
        return panelWidth - (data.getIntOr("Mail", 0) > 0 ? 135 : 20);
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
        name.setTextShadow(false);
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
        var values = request("configure", data.getIntOr("Page", 0));
        values.putBoolean(option, enabled);
        send(values);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name != null) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                var values = request("configure", data.getIntOr("Page", 0));
                values.putString("Name", name.getValue());
                endRename();
                send(values);
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                endRename();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        if (++refreshTicks % 30 == 0) {
            send(request("refresh", data.getIntOr("Page", 0)));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, Math.min(height - 3, panelTop + 236), 0xE92D241D);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFFD18856);
        if (titleButton == null) {
            graphics.text(font, visibleTitle(), panelLeft + 10, panelTop + 10, 0xFFF5E7D2, false);
        }
        graphics.text(font, tr("summary", data.getIntOr("Cargo", 0), data.getIntOr("Birds", 0), data.getIntOr("Fuel", 0), data.getIntOr("FuelCapacity", 0)),
                panelLeft + 10, panelTop + 26, 0xFFDFC4AA);
        if (data.getListOrEmpty("Rows").isEmpty()) {
            graphics.text(font, tr("empty"), panelLeft + 10, panelTop + 101, 0xFFDFC4AA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component nameOf(CompoundTag data) {
        String value = data.getStringOr("Name", "");
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

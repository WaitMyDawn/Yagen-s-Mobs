package yagen.waitmydawn.yagens_mobs.entity.salvage;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

final class SalvageRouteScreen extends Screen {
    private final int ray;
    private final boolean manage;
    private CompoundTag view;
    private int refresh;

    private SalvageRouteScreen(CompoundTag view) {
        super(Component.translatable(view.getStringOr("Mode", "").equals("Manage")
                ? "screen.yagens_mobs.salvage.manage" : "screen.yagens_mobs.salvage_routes"));
        manage = view.getStringOr("Mode", "").equals("Manage");
        this.view = view;
        ray = view.getIntOr("Ray", 0);
    }

    static void accept(SalvageRoutes.Snapshot message) {
        Minecraft client = Minecraft.getInstance();
        CompoundTag data = message.data();
        if (data.getBooleanOr("Open", false)) {
            client.setScreen(new SalvageRouteScreen(data));
        } else if (client.screen instanceof SalvageRouteScreen screen && screen.ray == data.getIntOr("Ray", 0)
                && screen.view.getLongOr("Origin", 0L) == data.getLongOr("Origin", 0L)
                && screen.view.getStringOr("Mode", "").equals(data.getStringOr("Mode", "")) && !screen.view.equals(data)) {
            screen.view = data;
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int left = width / 2 - 112;
        int top = height / 2 - 68;
        int page = view.getIntOr("Page", 0);
        for (Tag item : view.getListOrEmpty("Rows")) {
            CompoundTag row = (CompoundTag) item;
            String name = row.getStringOr("Name", "");
            Component title = name.isBlank() ? Component.translatable("block.yagens_mobs.salvage_nest_box") : Component.literal(name);
            Component bearing = Component.translatable("screen.yagens_mobs.salvage_bearing." + row.getIntOr("Bearing", 0));
            Component entry = Component.translatable("screen.yagens_mobs.salvage_route_entry", title, row.getIntOr("Distance", 0), bearing);
            var button = addRenderableWidget(Button.builder(entry, pressed -> choose(row)).bounds(left, top, 224, 20).build());
            button.active = row.getBooleanOr("Editable", false);
            if (manage) {
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.yagens_mobs.salvage.cut")));
            }
            top += 22;
        }
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> page(page - 1))
                .bounds(left, height / 2 + 67, 25, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("›"), button -> page(page + 1))
                .bounds(left + 199, height / 2 + 67, 25, 20).build()).active = page + 1 < view.getIntOr("Pages", 0);
    }

    private CompoundTag request(int page) {
        CompoundTag data = SalvageRoutes.action(manage ? "Manage" : "Ride");
        data.putInt("Ray", ray);
        if (manage) {
            data.putLong("Origin", view.getLongOr("Origin", 0L));
            SaveTag.putId(data, "OriginId", SaveTag.id(view, "OriginId").orElseThrow());
        }
        data.putInt("Page", page);
        return data;
    }

    private void choose(CompoundTag row) {
        CompoundTag data = request(view.getIntOr("Page", 0));
        data.putLong("Target", row.getLongOr("Pos", 0L));
        if (manage) {
            SaveTag.putId(data, "TargetId", SaveTag.id(row, "Id").orElseThrow());
        }
        ClientPlayNetworking.send(new SalvageRoutes.Request(data));
        if (!manage) {
            onClose();
        }
    }

    private void page(int page) {
        ClientPlayNetworking.send(new SalvageRoutes.Request(request(page)));
    }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) {
            page(view.getIntOr("Page", 0));
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        super.render(gui, mouseX, mouseY, tick);
        gui.drawCenteredString(font, title, width / 2, height / 2 - 85, 0xfff2e7fb);
        if (view.getListOrEmpty("Rows").isEmpty()) {
            gui.drawCenteredString(font, Component.translatable("screen.yagens_mobs.salvage.no_links"),
                    width / 2, height / 2 - 34, 0xffd0b9db);
        }
        gui.drawCenteredString(font, (view.getIntOr("Page", 0) + 1) + " / " + view.getIntOr("Pages", 0), width / 2, height / 2 + 73, 0xfff2e7fb);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

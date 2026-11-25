package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class SalvageRouteScreen extends Screen {
    private final int ray;
    private final boolean manage;
    private CompoundTag view;
    private int refresh;

    private SalvageRouteScreen(CompoundTag view) {
        super(Component.translatable(view.getString("Mode").equals("Manage")
                ? "screen.yagens_mobs.salvage.manage" : "screen.yagens_mobs.salvage_routes"));
        manage = view.getString("Mode").equals("Manage");
        this.view = view;
        ray = view.getInt("Ray");
    }

    static void accept(SalvageRoutes.Snapshot message) {
        Minecraft client = Minecraft.getInstance();
        CompoundTag data = message.data();
        if (data.getBoolean("Open")) {
            client.setScreen(new SalvageRouteScreen(data));
        } else if (client.screen instanceof SalvageRouteScreen screen && screen.ray == data.getInt("Ray")
                && screen.view.getLong("Origin") == data.getLong("Origin")
                && screen.view.getString("Mode").equals(data.getString("Mode")) && !screen.view.equals(data)) {
            screen.view = data;
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int left = width / 2 - 112;
        int top = height / 2 - 68;
        int page = view.getInt("Page");
        for (Tag item : view.getList("Rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            String name = row.getString("Name");
            Component title = name.isBlank() ? Component.translatable("block.yagens_mobs.salvage_nest_box") : Component.literal(name);
            Component bearing = Component.translatable("screen.yagens_mobs.salvage_bearing." + row.getInt("Bearing"));
            Component entry = Component.translatable("screen.yagens_mobs.salvage_route_entry", title, row.getInt("Distance"), bearing);
            var button = addRenderableWidget(Button.builder(entry, pressed -> choose(row)).bounds(left, top, 224, 20).build());
            button.active = row.getBoolean("Editable");
            if (manage) {
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.yagens_mobs.salvage.cut")));
            }
            top += 22;
        }
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> page(page - 1))
                .bounds(left, height / 2 + 67, 25, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("›"), button -> page(page + 1))
                .bounds(left + 199, height / 2 + 67, 25, 20).build()).active = page + 1 < view.getInt("Pages");
    }

    private CompoundTag request(int page) {
        CompoundTag data = SalvageRoutes.action(manage ? "Manage" : "Ride");
        data.putInt("Ray", ray);
        if (manage) {
            data.putLong("Origin", view.getLong("Origin"));
            data.putUUID("OriginId", view.getUUID("OriginId"));
        }
        data.putInt("Page", page);
        return data;
    }

    private void choose(CompoundTag row) {
        CompoundTag data = request(view.getInt("Page"));
        data.putLong("Target", row.getLong("Pos"));
        if (manage) {
            data.putUUID("TargetId", row.getUUID("Id"));
        }
        PacketDistributor.sendToServer(new SalvageRoutes.Request(data));
        if (!manage) {
            onClose();
        }
    }

    private void page(int page) {
        PacketDistributor.sendToServer(new SalvageRoutes.Request(request(page)));
    }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) {
            page(view.getInt("Page"));
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        super.render(gui, mouseX, mouseY, tick);
        gui.drawCenteredString(font, title, width / 2, height / 2 - 85, 0xf2e7fb);
        if (view.getList("Rows", Tag.TAG_COMPOUND).isEmpty()) {
            gui.drawCenteredString(font, Component.translatable("screen.yagens_mobs.salvage.no_links"),
                    width / 2, height / 2 - 34, 0xd0b9db);
        }
        gui.drawCenteredString(font, (view.getInt("Page") + 1) + " / " + view.getInt("Pages"), width / 2, height / 2 + 73, 0xf2e7fb);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

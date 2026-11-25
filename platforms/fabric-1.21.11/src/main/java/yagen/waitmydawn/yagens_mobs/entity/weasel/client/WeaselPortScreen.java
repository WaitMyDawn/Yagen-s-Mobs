package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

final class WeaselPortScreen extends Screen {
    private CompoundTag settings;
    private EditBox input;
    private Button apply;

    private WeaselPortScreen(CompoundTag settings) {
        super(text("title"));
        this.settings = settings;
    }

    static void open(CompoundTag settings) {
        var game = Minecraft.getInstance();
        if (settings.getStringOr("Session", "").isEmpty()) return;
        if (game.screen instanceof WeaselPortScreen screen
                && screen.settings.getStringOr("Session", "").equals(settings.getStringOr("Session", ""))) {
            screen.settings = settings;
            if (screen.input != null) screen.input.setValue(Integer.toString(settings.getIntOr("Priority", 99)));
        } else {
            game.setScreen(new WeaselPortScreen(settings));
        }
    }

    @Override
    protected void init() {
        int x = width / 2 - 69;
        int y = height / 2 + 3;
        input = new EditBox(font, x, y, 50, 20, text("priority_label"));
        input.setMaxLength(3);
        input.setFilter(value -> value.chars().allMatch(character -> character >= '0' && character <= '9'));
        input.setValue(Integer.toString(settings.getIntOr("Priority", 99)));
        apply = Button.builder(text("apply"), ignored -> commit()).bounds(x + 56, y, 82, 20).build();
        input.setResponder(value -> apply.active = !value.isEmpty());
        addRenderableWidget(input);
        addRenderableWidget(apply);
        setInitialFocus(input);
        input.setHighlightPos(0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 37, 0xFFFFFFFF);
        graphics.drawCenteredString(font, text("priority_hint", text("mode." + settings.getStringOr("Mode", "take"))),
                width / 2, height / 2 - 19, 0xFFE0E0E0);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            commit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        if (!carryingWeasel()) onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private boolean carryingWeasel() {
        var player = Minecraft.getInstance().player;
        int id = settings.getIntOr("Weasel", -1);
        return player != null && player.getPassengers().stream().anyMatch(mob -> mob.getId() == id && mob.isAlive());
    }

    private void commit() {
        if (!carryingWeasel() || input.getValue().isEmpty()) return;
        var change = new CompoundTag();
        change.putBoolean("Storage", true);
        change.putString("Session", settings.getStringOr("Session", ""));
        change.putInt("Priority", Integer.parseInt(input.getValue()));
        WeaselClient.request(change);
    }

    private static Component text(String key, Object... values) {
        return Component.translatable("screen.yagens_mobs.weasel_storage." + key, values);
    }
}

package yagen.waitmydawn.yagens_mobs.guide.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.api.IComponentRenderContext;

final class GuideUi {
    static void control(IComponentRenderContext ctx, int page, int x, int y, int w, String label,
            Supplier<Component> narration, Runnable action, BooleanSupplier enabled) {
        ctx.addWidget(new Button(x, y + 18, w, label.equals("↕") ? 16 : 12, Component.literal(label), b -> { if (enabled.getAsBoolean()) action.run(); }, s -> s.get()) {
            @Override
            protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                return narration.get().copy();
            }
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                active = enabled.getAsBoolean();
                if (active && isFocused())
                    g.renderOutline(getX(), getY(), width, height, GuidePlates.ACCENT);
            }
        }, page);
    }

    static void clock(GuiGraphics g, boolean morning) {
        var image = ResourceLocation.tryParse(morning ? "minecraft:textures/item/clock_00.png" : "minecraft:textures/item/clock_32.png");
        g.blit(image, 7, 0, 0, 0, 16, 16, 16, 16);
    }

    static void portrait(GuiGraphics g, String path, int x, int y) {
        g.blit(GuidePlates.id(path), x, y, 52, 38, 0, 0, 128, 96, 128, 96);
    }

    static void upload(String path, NativeImage image) {
        Minecraft.getInstance().getTextureManager().register(GuidePlates.id(path), new DynamicTexture(image));
    }

    private GuideUi() {}
}

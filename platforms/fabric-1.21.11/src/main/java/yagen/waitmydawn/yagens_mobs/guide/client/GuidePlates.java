package yagen.waitmydawn.yagens_mobs.guide.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import yagen.waitmydawn.yagens_mobs.guide.FieldNotes;

public final class GuidePlates {
    static final int WIDTH = 116;
    static final int INK = 0xff463527, ACCENT = 0xff835137, RULE = 0xffbca47f;
    private static final Map<String, Mob> models = new HashMap<>();
    private static ClientLevel modelLevel;

    static void setNotes(Map<String, Long> known) { SpecimenPages.open(known); }

    public static void clear() {
        SpecimenPages.open(Map.of());
        PortraitTextures.clear();
        models.clear();
        modelLevel = null;
    }

    static Component label(String key) { return Component.translatable("guide.yagens_mobs." + key); }
    static Component name(String species) { return Component.translatable("entity.yagens_mobs." + species); }
    static Identifier id(String path) { return Identifier.fromNamespaceAndPath("yagens_mobs", path); }

    static void heading(GuiGraphics g, Component title) {
        centered(g, title.getString(), WIDTH / 2, 0, WIDTH - 6, ACCENT);
        g.fill(10, 12, WIDTH - 10, 13, RULE);
    }

    static void centered(GuiGraphics g, String text, int cx, int y, int maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1, (float) maxWidth / Math.max(1, font.width(text)));
        g.pose().pushMatrix();
        g.pose().translate((float)cx, (float)y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popMatrix();
    }

    record Card(String species, int x, int y, int w, int h) {}
    private static final int COLUMNS = 3, ROWS = 2, CARDS_PER_PAGE = COLUMNS * ROWS;
    private static final int CARD_WIDTH = 37, CARD_HEIGHT = 62, COLUMN_GAP = 2, ROW_GAP = 6;

    static int catalogPages() {
        return (FieldNotes.SPECIES.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE;
    }

    static List<Card> cards(int page) {
        int start = page * CARDS_PER_PAGE;
        int count = Math.min(CARDS_PER_PAGE, FieldNotes.SPECIES.size() - start);
        var cards = new ArrayList<Card>();
        for (int slot = 0; slot < count; slot++) {
            int x = (WIDTH - COLUMNS * CARD_WIDTH - (COLUMNS - 1) * COLUMN_GAP) / 2
                    + slot % COLUMNS * (CARD_WIDTH + COLUMN_GAP);
            int y = 3 + slot / COLUMNS * (CARD_HEIGHT + ROW_GAP);
            cards.add(new Card(FieldNotes.SPECIES.get(start + slot), x, y, CARD_WIDTH, CARD_HEIGHT));
        }
        return List.copyOf(cards);
    }

    static void catalogFooter(GuiGraphics graphics, int page) {
        if (page < catalogPages()) {
            centered(graphics, Integer.toString(page + 1), WIDTH / 2, 143, WIDTH, INK);
        }
    }

    static void card(GuiGraphics g, Card card, boolean highlighted) {
        var mc = Minecraft.getInstance();
        if (mc.level != modelLevel) {
            models.clear();
            modelLevel = mc.level;
        }
        int x = card.x(), y = card.y(), w = card.w(), h = card.h();
        int line = highlighted ? ACCENT : RULE;
        g.fill(x, y, x+w, y+h, line);
        g.fill(x+1, y+1, x+w-1, y+h-1, highlighted ? 0xfff8ebcc : 0xffede1c5);
        Component caption = name(card.species());
        var lines = mc.font.split(caption, w-4);
        boolean wrap = lines.size() == 2 && mc.font.width(caption) > w-1;
        int footer = 20;
        g.fill(x+3, y+h-footer-1, x+w-3, y+h-footer, line);
        Mob model = models.computeIfAbsent(card.species(), s -> (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(id(s)).create(mc.level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED));
        if (model != null) {
            float factor = switch (card.species()) {
                case "velocipod" -> .18f; case "salvage_ray" -> .22f; case "condroc" -> .30f;
                case "lumen_butterfly" -> 1.7f; case "snowcache_weasel" -> .49f;
                case "kavat" -> .27f; case "desert_skate", "bubble_salamander" -> .39f; default -> .49f;
            };
            model.tickCount = mc.player.tickCount;

            // The inventory renderer submits a picture-in-picture rectangle in screen coordinates.
            var start = g.pose().transformPosition(x+1, y+1, new org.joml.Vector2f());
            var end = g.pose().transformPosition(x+w-1, y+h-footer-1, new org.joml.Vector2f());
            float scale = (end.x-start.x)/(w-2);
            var state = mc.getEntityRenderDispatcher().getRenderer(model).createRenderState(model, 1);
            state.lightCoords = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            var rotation = new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateX(-.32f).rotateY(2.22f);
            g.submitEntityRenderState(state, Math.min(w,h-footer)*factor*scale,
                    new org.joml.Vector3f(0,model.getBbHeight()*.36f,0), rotation, null,
                    Math.round(start.x),Math.round(start.y),Math.round(end.x),Math.round(end.y));

        }
        if (wrap) {
            for (int i = 0; i < lines.size(); i++) {
                var lineText = lines.get(i);
                g.drawString(mc.font, lineText, x+(w-mc.font.width(lineText))/2, y+h-18+i*9, INK, false);
            }
        } else centered(g, caption.getString(), x+w/2, y+h-14, w-1, INK);
    }


    private GuidePlates() {}
}

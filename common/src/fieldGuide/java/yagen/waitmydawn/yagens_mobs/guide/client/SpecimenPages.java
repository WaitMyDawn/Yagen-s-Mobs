package yagen.waitmydawn.yagens_mobs.guide.client;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.IComponentRenderContext;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenColor;
import yagen.waitmydawn.yagens_mobs.guide.FieldNotes;

final class SpecimenPages {
    private static final List<String> SECTIONS =
            List.of("attributes", "habitat", "loot", "taming", "breeding", "behavior");
    private static final List<String> AXES = List.of("breed", "coat", "ear", "tail");
    private static final int PAGE_CONTENT_HEIGHT = 134;
    private static final int PLANT_COLUMNS = 3;
    private static final int PLANT_COLUMN_WIDTH = 38;
    private static final int PLANT_ROW_HEIGHT = 27;
    private static final List<LumenColor> TAMING_COLORS = List.of(LumenColor.values());
    private static Map<String, Long> notes = Map.of();
    private static final Map<String, Layout> layouts = new HashMap<>();
    private static final Map<String, Integer> spreads = new HashMap<>();
    private static int sort;
    private record Line(FormattedCharSequence text, int y, int color) {}
    private record PlantGrid(int page, int y) {
        private static final int HEIGHT =
                ((TAMING_COLORS.size() + PLANT_COLUMNS - 1) / PLANT_COLUMNS) * PLANT_ROW_HEIGHT;
    }
    private record Layout(List<List<Line>> text, List<String> keys, int total, PlantGrid plants) {
        int pages() {
            return text.size() + Math.max(1, (keys.size() + 3) / 4);
        }
    }
    static void open(Map<String, Long> known) {
        notes = Map.copyOf(known);
        layouts.clear();
        spreads.clear();
    }
    private static Layout layout(String species) {
        return layouts.computeIfAbsent(species, s -> {
            var font = Minecraft.getInstance().font;
            List<List<Line>> pages = new ArrayList<>();
            List<Line> lines = new ArrayList<>();
            pages.add(lines);
            int y = 0;
            PlantGrid plants = null;
            for (String section : SECTIONS) {
                var description = font.split(GuidePlates.label(s + "." + section), 110);
                boolean showsTamingPlants = s.equals("lumen_butterfly") && section.equals("taming");
                int required = showsTamingPlants
                        ? Math.min(PAGE_CONTENT_HEIGHT, 15 + description.size() * 10 + PlantGrid.HEIGHT)
                        : 12 + Math.min(2, description.size()) * 10;
                if (y + required > PAGE_CONTENT_HEIGHT) {
                    lines = new ArrayList<>();
                    pages.add(lines);
                    y = 0;
                }
                lines.add(new Line(GuidePlates.label(section).getVisualOrderText(), y, GuidePlates.ACCENT));
                y += 12;
                for (var line : description) {
                    if (y + 9 > PAGE_CONTENT_HEIGHT) {
                        lines = new ArrayList<>();
                        pages.add(lines);
                        y = 0;
                    }
                    lines.add(new Line(line, y, GuidePlates.INK));
                    y += 10;
                }
                if (showsTamingPlants) {
                    if (y + 3 + PlantGrid.HEIGHT > PAGE_CONTENT_HEIGHT) {
                        lines = new ArrayList<>();
                        pages.add(lines);
                        y = 0;
                    }
                    plants = new PlantGrid(pages.size() - 1, y + 3);
                    y += 3 + PlantGrid.HEIGHT;
                }
                y += 4;
            }
            List<String> variants = FieldNotes.variants(s);
            var found = new ArrayList<String>();
            for (String v : variants)
                if (notes.containsKey(s + "/" + v))
                    found.add(v);
            Comparator<String> dictionary = Comparator.naturalOrder();
            if (s.equals("kavat")) {
                var order = Arrays.stream(ClientConfigs.KAVAT_SORT_ORDER.get().split(","))
                                    .map(String::trim)
                                    .mapToInt(AXES::indexOf)
                                    .toArray();
                dictionary = Comparator.comparing(v -> {
                    var values = v.split("/");
                    return Arrays.stream(order).mapToObj(i -> values[i]).collect(Collectors.joining("/"));
                });
            }
            if (sort == 2)
                found.sort(dictionary);
            else {
                Comparator<String> cmp = Comparator.comparingLong(v -> notes.get(s + "/" + v));
                found.sort((sort == 0 ? cmp : cmp.reversed()).thenComparing(dictionary));
            }
            return new Layout(pages, found, variants.size(), plants);
        });
    }
    private static int index(String s, int side) {
        return spreads.getOrDefault(s, 0) * 2 + side;
    }
    static void controls(IComponentRenderContext ctx, String s, int side, int page) {
        button(ctx, page, side == 0 ? 0 : 95, 139, 21, side == 0 ? "◀" : "▶", () -> {
            int max = (layout(s).pages() - 1) / 2;
            spreads.compute(s, (k, v) -> Math.max(0, Math.min((v == null ? 0 : v) + (side == 0 ? -1 : 1), max)));
        }, () -> side == 0 ? index(s, side) > 0 : index(s, side) + 1 < layout(s).pages());
        button(ctx, page, 7, 0, 16, "↕", () -> {
            sort = (sort + 1) % 3;
            layouts.clear();
        }, () -> index(s, side) == layout(s).text.size());
    }
    private static void button(IComponentRenderContext ctx, int page, int x, int y, int w, String label,
            Runnable action, java.util.function.BooleanSupplier enabled) {
        GuideUi.control(ctx, page, x, y, w, label,
                () -> "↕".equals(label) ? GuidePlates.label("order." + sort) : Component.literal(label), action, enabled);
    }
    static void render(GuiGraphics g, IComponentRenderContext ctx, String species, int side, int mx, int my) {
        var l = layout(species);
        int i = index(species, side);
        if (i >= l.pages())
            return;
        var font = Minecraft.getInstance().font;
        if (i < l.text.size()) {
            for (var line : l.text.get(i)) g.drawString(font, line.text, 2, line.y, line.color, false);
            if (l.plants != null && l.plants.page == i) renderTamingPlants(g, ctx, l.plants.y, mx, my);
        } else {
            boolean first = i == l.text.size();
            if (first) {
                if (sort == 2) {
                    g.renderItem(net.minecraft.world.item.Items.BOOK.getDefaultInstance(), 7, 0);
                } else {
                    GuideUi.clock(g, sort == 0);
                }
                GuidePlates.centered(g, Component.translatable("guide.yagens_mobs.recorded_count",
                        l.keys.size(), l.total).getString(), 68, 4, 85, GuidePlates.ACCENT);
                g.fill(5, 18, 111, 19, GuidePlates.RULE);
                if (ctx.isAreaHovered(mx, my, 7, 0, 16, 16))
                    ctx.setHoverTooltipComponents(List.of(GuidePlates.label("order." + sort)));
            }
            if (l.keys.isEmpty()) {
                int y = 38;
                for (var line : font.split(GuidePlates.label("observe"), 109)) {
                    g.drawString(font, line, 3, y, GuidePlates.INK, false);
                    y += 10;
                }
            }
            int start = (i - l.text.size()) * 4;
            for (int n = 0; n < 4 && start + n < l.keys.size(); n++) {
                String v = l.keys.get(start + n), key = species + "/" + v;
                int x = 2 + 57 * (n % 2), y = (first ? 26 : 4) + 48 * (n / 2);
                var photo = PortraitTextures.get(key);
                g.renderOutline(x, y, 54, 40, GuidePlates.RULE);
                if (photo != null)
                    GuideUi.portrait(g, photo, x + 1, y + 1);
                String caption = Arrays.stream(v.split("/"))
                                         .map(k
                                                 -> (species.equals("kavat")
                                                                 ? Component.translatable("kavat.trait." + k)
                                                                 : GuidePlates.label("variant." + k))
                                                         .getString())
                                         .collect(Collectors.joining(" · "));
                if (ctx.isAreaHovered(mx, my, x, y, 54, 40)) {
                    long ticks = notes.get(key);
                    var time = ticks < 0 ? GuidePlates.label("date_unknown")
                                         : Component.translatable("guide.yagens_mobs.date",
                                                   String.format(Locale.ROOT, "%.1f", ticks / 24000d));
                    ctx.setHoverTooltipComponents(List.of(Component.literal(caption), time));
                }
            }
        }
        GuidePlates.centered(g, (i + 1) + " / " + l.pages(), 58, 140, 65, GuidePlates.INK);
        if (side == 0 && i > 0)
            GuidePlates.centered(g, "◀", 10, 140, 18, GuidePlates.ACCENT);
        if (side == 1 && i + 1 < l.pages())
            GuidePlates.centered(g, "▶", 106, 140, 18, GuidePlates.ACCENT);
    }
    private static void renderTamingPlants(GuiGraphics g, IComponentRenderContext ctx, int top, int mx, int my) {
        for (int i = 0; i < TAMING_COLORS.size(); i++) {
            var color = TAMING_COLORS.get(i);
            var stack = new ItemStack(color.plant());
            int x = 1 + PLANT_COLUMN_WIDTH * (i % PLANT_COLUMNS);
            int y = top + PLANT_ROW_HEIGHT * (i / PLANT_COLUMNS);
            g.renderItem(stack, x + (PLANT_COLUMN_WIDTH - 16) / 2, y);
            var label = GuidePlates.label("variant." + color.id());
            GuidePlates.centered(g, label.getString(), x + PLANT_COLUMN_WIDTH / 2, y + 17,
                    PLANT_COLUMN_WIDTH, GuidePlates.INK);
            if (ctx.isAreaHovered(mx, my, x, y, PLANT_COLUMN_WIDTH, PLANT_ROW_HEIGHT))
                ctx.setHoverTooltipComponents(List.of(stack.getHoverName()));
        }
    }

    private SpecimenPages() {}
}

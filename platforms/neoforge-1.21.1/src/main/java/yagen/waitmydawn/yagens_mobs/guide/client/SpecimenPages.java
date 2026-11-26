package yagen.waitmydawn.yagens_mobs.guide.client;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import vazkii.patchouli.api.IComponentRenderContext;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.guide.FieldNotes;

final class SpecimenPages {
    private static final List<String> SECTIONS =
            List.of("attributes", "habitat", "loot", "taming", "breeding", "behavior");
    private static final List<String> AXES = List.of("breed", "coat", "ear", "tail");
    private static Map<String, Long> notes = Map.of();
    private static final Map<String, Layout> layouts = new HashMap<>();
    private static final Map<String, Integer> spreads = new HashMap<>();
    private static int sort;
    private record Line(FormattedCharSequence text, int y, int color) {}
    private record Layout(List<List<Line>> text, List<String> keys, int total) {
        int pages() {
            return text.size() + Math.max(1, Math.ceilDiv(keys.size(), 4));
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
            for (String section : SECTIONS) {
                if (y + 23 > 134) {
                    lines = new ArrayList<>();
                    pages.add(lines);
                    y = 0;
                }
                lines.add(new Line(GuidePlates.label(section).getVisualOrderText(), y, GuidePlates.ACCENT));
                y += 12;
                for (var line : font.split(GuidePlates.label(s + "." + section), 110)) {
                    if (y + 9 > 134) {
                        lines = new ArrayList<>();
                        pages.add(lines);
                        y = 0;
                    }
                    lines.add(new Line(line, y, GuidePlates.INK));
                    y += 10;
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
            return new Layout(pages, found, variants.size());
        });
    }
    private static int index(String s, int side) {
        return spreads.getOrDefault(s, 0) * 2 + side;
    }
    static void controls(IComponentRenderContext ctx, String s, int side, int page) {
        button(ctx, page, side == 0 ? 0 : 95, 139, 21, side == 0 ? "◀" : "▶", () -> {
            int max = (layout(s).pages() - 1) / 2;
            spreads.compute(s, (k, v) -> Math.clamp((v == null ? 0 : v) + (side == 0 ? -1 : 1), 0, max));
        }, () -> side == 0 ? index(s, side) > 0 : index(s, side) + 1 < layout(s).pages());
        button(ctx, page, 7, 0, 16, "↕", () -> {
            sort = (sort + 1) % 3;
            layouts.clear();
        }, () -> index(s, side) == layout(s).text.size());
    }
    private static void button(IComponentRenderContext ctx, int page, int x, int y, int w, String label,
            Runnable action, java.util.function.BooleanSupplier enabled) {
        ctx.addWidget(new Button(x, y + 18, w, label.equals("↕") ? 16 : 12, Component.literal(label), b -> action.run(), s -> s.get()) {
            @Override
            protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                return "↕".equals(label) ? GuidePlates.label("order."+sort).copy() : super.createNarrationMessage();
            }
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                active = enabled.getAsBoolean();
                if (active && isFocused())
                    g.renderOutline(getX(), getY(), width, height, GuidePlates.ACCENT);
            }
            @Override
            public void onPress() {
                if (enabled.getAsBoolean())
                    super.onPress();
            }
        }, page);
    }
    static void render(GuiGraphics g, IComponentRenderContext ctx, String species, int side, int mx, int my) {
        var l = layout(species);
        int i = index(species, side);
        if (i >= l.pages())
            return;
        var font = Minecraft.getInstance().font;
        if (i < l.text.size()) {
            for (var line : l.text.get(i)) g.drawString(font, line.text, 2, line.y, line.color, false);
        } else {
            boolean first = i == l.text.size();
            if (first) {
                if (sort == 2) {
                    g.renderItem(net.minecraft.world.item.Items.BOOK.getDefaultInstance(), 7, 0);
                } else {
                    var clock = net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                            sort == 0 ? "item/clock_00" : "item/clock_32");
                    var sprite = Minecraft.getInstance().getTextureAtlas(
                            net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(clock);
                    g.blit(7, 0, 0, 16, 16, sprite);
                }
                GuidePlates.centered(g, Component.translatable("guide.yagens_mobs.recorded_count",
                        l.keys.size(), l.total).getString(), 68, 4, 85, GuidePlates.ACCENT);
                g.fill(5, 18, 111, 19, GuidePlates.RULE);
                if (ctx.isAreaHovered(mx, my, 7, 0, 16, 16))
                    ctx.setHoverTooltipComponents(List.of(GuidePlates.label("order." + sort)));
            }
            if (l.keys.isEmpty())
                g.drawWordWrap(font, GuidePlates.label("observe"), 3, 38, 109, GuidePlates.INK);
            int start = (i - l.text.size()) * 4;
            for (int n = 0; n < 4 && start + n < l.keys.size(); n++) {
                String v = l.keys.get(start + n), key = species + "/" + v;
                int x = 2 + 57 * (n % 2), y = (first ? 26 : 4) + 48 * (n / 2);
                var photo = PortraitTextures.get(key);
                g.renderOutline(x, y, 54, 40, GuidePlates.RULE);
                if (photo != null)
                    g.blit(photo, x + 1, y + 1, 52, 38, 0, 0, 128, 96, 128, 96);
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
    private SpecimenPages() {}
}

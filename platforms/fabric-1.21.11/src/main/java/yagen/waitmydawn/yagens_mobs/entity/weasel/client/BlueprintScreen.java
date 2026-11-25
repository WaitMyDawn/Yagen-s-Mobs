package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintPlan;
import yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintPlanner;
import yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintRecipes;

final class BlueprintScreen extends Screen {
    private static final int CARD_W = 146;
    private static final int CARD_H = 105;
    private static final int BRANCH_X = 183;
    private static final int GAP_Y = 18;
    private static final int TOP = 66;
    private static final int SURFACE = 0xffe8e4d6;
    private static final int INK = 0xff273944;
    private static final int EDGE = 0xff728790;

    private final UUID session;
    private final Map<String, BlueprintPlan.Choice> choices = new LinkedHashMap<>();
    private final Map<Identifier, List<WeaselJei.View>> viewCache = new HashMap<>();
    private final List<Node> placed = new ArrayList<>();
    private List<MaterialRow> materialRows = List.of();
    private List<ItemStack> results = List.of();
    private final BlueprintRecipes known;
    private @Nullable Identifier output;
    private @Nullable Node root;
    private EditBox search;
    private EditBox count;
    private Button save;
    private Button fit;
    private String restoredFilter;
    private String lastQuery = "\u0000";
    private int filteredAfter;
    private int ticks;
    private int searchScroll;
    private int totalsScroll;
    private double panX = 13;
    private double panY = 12;
    private float zoom = 1;
    private boolean panning;
    private boolean choosing = true;
    private int requested = 1;

    private record MaterialRow(@Nullable String section, @Nullable Identifier item, long count) {}

    private static final class Node {
        final String path;
        final BlueprintPlan.Choice choice;
        final List<WeaselJei.View> views;
        final int page;
        final List<Node> children = new ArrayList<>();
        int x;
        int y;
        int pivotY;
        int span;

        Node(String path, BlueprintPlan.Choice choice, List<WeaselJei.View> views, int page) {
            this.path = path;
            this.choice = choice;
            this.views = views;
            this.page = page;
        }

        WeaselJei.View view() { return views.get(page); }
    }

    BlueprintScreen(UUID session, @Nullable BlueprintPlan previous) {
        super(tr("title"));
        this.session = session;
        var level = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().level);
        known = WeaselJei.catalog(level);
        if (previous != null) {
            output = previous.output();
            requested = previous.quantity();
            choices.putAll(previous.choices());
            choosing = false;
            rebuild();
        }
    }

    private static boolean nativeLayoutAvailable() {
        return Minecraft.getInstance().isRunning() && WeaselJei.runtime() != null;
    }

    int editorWidth() { return editorWidth(width); }
    static int editorWidth(int width) { return Math.max(width * 77 / 100, width - 128); }
    private int summaryWidth() { return Math.min(138, Math.max(96, editorWidth() / 4)); }
    private int divider() { return editorWidth() - summaryWidth(); }
    public Rect2i dropTarget() { return new Rect2i(8, 34, 24, 24); }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.yagens_mobs.recipe_blueprint." + key, args); }
    private int quantity() {
        if (count == null) return requested;
        try {
            return Mth.clamp(Integer.parseInt(count.getValue()), 1, BlueprintPlan.MAX_QUANTITY);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    @Override
    protected void init() {
        if (restoredFilter == null && WeaselJei.runtime() != null) restoredFilter = WeaselJei.runtime().getIngredientFilter().getFilterText();
        int limit = editorWidth();
        search = addRenderableWidget(new EditBox(font, 38, 37, Math.max(48, limit - 191), 18, tr("search")));
        search.setMaxLength(128);
        search.setHint(tr("search"));
        search.setResponder(query -> {
            choosing = true;
            if (fit != null) fit.active = false;
            if (save != null) save.active = false;
            searchScroll = 0;
            filteredAfter = ticks + 2;
        });
        count = addRenderableWidget(new EditBox(font, limit - 141, 37, 52, 18, tr("quantity")));
        count.setMaxLength(7);
        count.setFilter(value -> value.chars().allMatch(Character::isDigit));
        count.setValue(Integer.toString(requested));
        count.setResponder(value -> {
            requested = quantity();
            updateTotals();
        });
        save = addRenderableWidget(Button.builder(tr("save"), button -> save()).bounds(limit - 83, 36, 75, 20).build());
        save.active = root != null;
        fit = addRenderableWidget(Button.builder(tr("fit"), button -> fitGraph()).bounds(limit - 87, 6, 79, 18).build());
        fit.active = root != null && !choosing;
        if (output == null) setInitialFocus(search);
        else fitGraph();
    }

    @Override
    public void tick() {
        ticks++;
        if (choosing && ticks >= filteredAfter && !search.getValue().equals(lastQuery)) {
            lastQuery = search.getValue();
            filter(lastQuery);
        }
        if (!nativeLayoutAvailable()) return;
        var visibleLayouts = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<mezz.jei.api.gui.IRecipeLayoutDrawable<?>, Boolean>());
        for (var node : placed) {
            var layout = node.view().layout();
            if (layout != null && visibleLayouts.add(layout)) layout.tick();
        }
    }

    private void filter(String query) {
        var runtime = WeaselJei.runtime();
        if (runtime == null) return;
        runtime.getIngredientFilter().setFilterText(query);
        var unique = new LinkedHashMap<Identifier, ItemStack>();
        for (ItemStack stack : runtime.getIngredientFilter().getFilteredItemStacks()) {
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!known.forOutput(key).isEmpty()) {
                unique.putIfAbsent(key, stack);
                if (unique.size() >= 192) break;
            }
        }
        if (!query.isBlank()) {
            var direct = Identifier.tryParse(query.toLowerCase(Locale.ROOT));
            if (direct != null && !known.forOutput(direct).isEmpty()) {
                unique.putFirst(direct, new ItemStack(BuiltInRegistries.ITEM.getValue(direct)));
            }
        }
        results = List.copyOf(unique.values());
    }

    void selectItem(ItemStack stack) {
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var views = views(item);
        if (views.isEmpty()) return;
        choosing = false;
        setFocused(null);
        if (!Objects.equals(output, item)) {
            choices.clear();
            output = item;
            choices.put("", new BlueprintPlan.Choice(item, views.getFirst().entry().id(), BlueprintPlan.Mode.ANY));
            panX = 13;
            panY = 12;
            zoom = 1;
        }
        rebuild();
        fitGraph();
    }

    private List<WeaselJei.View> views(Identifier item) {
        // The shared drawable is repositioned before each render and hit test.
        return viewCache.computeIfAbsent(item, key -> WeaselJei.recipes(key, known));
    }

    private void rebuild() {
        int oldRootY = root == null || !root.choice.item().equals(output) ? 0 : root.y;
        placed.clear();
        var choice = output == null ? null : choices.getOrDefault("", new BlueprintPlan.Choice(output, null, BlueprintPlan.Mode.ANY));
        root = choice == null ? null : build("", choice);
        if (root != null) {
            layout(root, 0, 0);
            panY += (oldRootY - root.y) * zoom;
        }
        updateTotals();
        if (save != null) save.active = root != null && !choosing;
        if (fit != null) fit.active = root != null && !choosing;
    }

    private @Nullable Node build(String path, BlueprintPlan.Choice choice) {
        List<WeaselJei.View> recipeViews = views(choice.item());
        if (recipeViews.isEmpty()) return null;
        int page = 0;
        for (int i = 0; i < recipeViews.size(); i++) {
            if (recipeViews.get(i).entry().id().equals(choice.recipe())) {
                page = i;
                break;
            }
        }
        choices.put(path, new BlueprintPlan.Choice(choice.item(), recipeViews.get(page).entry().id(), choice.mode()));
        var node = new Node(path, choice, recipeViews, page);
        placed.add(node);
        var ingredients = node.view().entry().ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            String child = BlueprintPlan.child(path, i);
            var childChoice = choices.get(child);
            if (childChoice != null && ingredients.get(i).test(stack(childChoice.item()))) {
                var next = build(child, childChoice);
                if (next != null) node.children.add(next);
            }
        }
        return node;
    }

    private int layout(Node node, int depth, int top) {
        int height = 0;
        for (var child : node.children) {
            height += layout(child, depth + 1, top + height);
        }
        node.span = Math.max(CARD_H + GAP_Y, height);
        node.x = depth * BRANCH_X;
        node.y = top + (node.span - CARD_H - GAP_Y) / 2;
        node.pivotY = node.y + CARD_H / 2;
        return node.span;
    }

    private void fitGraph() {
        if (root == null) return;
        int right = 0;
        int bottom = 0;
        for (var node : placed) {
            right = Math.max(right, node.x + CARD_W);
            bottom = Math.max(bottom, node.y + CARD_H);
        }
        zoom = Mth.clamp(Math.min((divider() - 22F) / right, (height - TOP - 24F) / bottom), .08F, 1F);
        panX = 11;
        panY = 12;
    }

    private void updateTotals() {
        totalsScroll = 0;
        if (root == null) {
            materialRows = List.of();
            return;
        }

        var totals = BlueprintPlanner.materials(new BlueprintPlan(output, requested, choices), known);
        var expanded = totals.expanded();
        var rows = new ArrayList<MaterialRow>();
        boolean showBreakdown = !totals.direct().equals(expanded);
        if (showBreakdown) {
            rows.add(new MaterialRow("materials_direct", null, 0));
            totals.direct().forEach((item, count) -> rows.add(new MaterialRow(null, item, count)));
            rows.add(new MaterialRow("materials_expanded", null, 0));
        }
        expanded.forEach((item, count) -> rows.add(new MaterialRow(null, item, count)));
        materialRows = List.copyOf(rows);
    }

    private void save() {
        if (root == null) return;
        var data = new CompoundTag();
        yagen.waitmydawn.yagens_mobs.compat.SaveTag.putId(data, "Session", session);
        data.put("Plan", new BlueprintPlan(output, quantity(), choices).save());
        WeaselClient.request(data);
        onClose();
    }

    @Override
    public void removed() {
        if (WeaselJei.runtime() != null && restoredFilter != null) WeaselJei.runtime().getIngredientFilter().setFilterText(restoredFilter);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // The canvas paints its own opaque background behind the graph and widgets.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!Minecraft.getInstance().isRunning()) return;
        int end = editorWidth();
        g.fill(0, 0, end, height, 0xff1b2730);
        g.fill(0, TOP, divider(), height, 0xff203039);
        g.fill(divider(), TOP, end, height, 0xff214459);
        g.drawString(font, title, 8, 10, 0xfff0e8d5);
        g.drawString(font, tr("quantity"), end - 141, 24, 0xffb9ced6);
        g.drawString(font, tr("materials"), divider() + 8, TOP + 9, 0xffeef4ed);
        var rect = dropTarget();
        g.fill(rect.getX(), rect.getY(), rect.getX() + 24, rect.getY() + 24, 0xff607989);
        if (output != null) g.renderItem(stack(output), rect.getX() + 4, rect.getY() + 4);
        else g.drawCenteredString(font, "+", rect.getX() + 12, rect.getY() + 7, 0xffffffff);
        ItemStack hovered;
        if (choosing) hovered = renderResults(g, mouseX, mouseY);
        else hovered = renderGraph(g, mouseX, mouseY);
        ItemStack material = renderTotals(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
        if (!material.isEmpty()) hovered = material;
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        if (inside(rect, mouseX, mouseY)) g.setTooltipForNextFrame(font, tr("drop"), mouseX, mouseY);
    }

    private ItemStack renderResults(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, tr("choose"), 9, TOP + 8, 0xffe1e7e5);
        int cols = Math.max(1, (divider() - 17) / 24);
        int rows = Math.max(1, (height - TOP - 31) / 24);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < cols * rows && searchScroll * cols + i < results.size(); i++) {
            var item = results.get(searchScroll * cols + i);
            int x = 8 + i % cols * 24;
            int y = TOP + 23 + i / cols * 24;
            g.fill(x, y, x + 20, y + 20, 0xff426071);
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                g.fill(x, y, x + 20, y + 20, 0xff5c8795);
                hovered = item;
            }
            g.renderItem(item, x + 2, y + 2);
        }
        return hovered;
    }

    private ItemStack renderTotals(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int visible = Math.min(materialRows.size() - totalsScroll, Math.max(1, (height - TOP - 25) / 20));
        for (int i = 0; i < visible; i++) {
            var row = materialRows.get(totalsScroll + i);
            int x = divider() + 9;
            int y = TOP + 24 + i * 20;
            if (row.section() != null) {
                g.drawString(font, tr(row.section()), x, y + 5, 0xffa4c7da, false);
                continue;
            }
            var icon = stack(row.item());
            String count = row.count() + " ×";
            int iconX = x + font.width(count) + 4;
            g.drawString(font, count, x, y + 5, 0xffe4f4ff);
            g.renderItem(icon, iconX, y);
            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= y && mouseY < y + 16) hovered = icon;
        }
        return hovered;
    }

    private ItemStack renderGraph(GuiGraphics g, int mouseX, int mouseY) {
        int ux = canvasX(mouseX);
        int uy = canvasY(mouseY);
        boolean enabled = mouseX < divider() && mouseY >= TOP;
        ItemStack hover = ItemStack.EMPTY;
        g.enableScissor(0, TOP, divider(), height);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) panX, (float) (TOP + panY));
        pose.scale(zoom, zoom);
        for (var node : placed) {
            for (var leaf : node.children) {
                int middle = node.x + CARD_W + (BRANCH_X - CARD_W) / 2;
                g.fill(node.x + CARD_W, node.pivotY, middle, node.pivotY + 2, EDGE);
                g.fill(middle, Math.min(node.pivotY, leaf.pivotY), middle + 2, Math.max(node.pivotY, leaf.pivotY) + 2, EDGE);
                g.fill(middle, leaf.pivotY, leaf.x, leaf.pivotY + 2, EDGE);
            }
        }
        for (var node : placed) {
            if (node.x * zoom + panX > divider() || (node.x + CARD_W) * zoom + panX < 0
                    || node.y * zoom + panY + TOP > height || (node.y + CARD_H) * zoom + panY < 0) continue;
            ItemStack picked = renderNode(g, node, enabled ? ux : Integer.MIN_VALUE, enabled ? uy : Integer.MIN_VALUE);
            if (!picked.isEmpty()) hover = picked;
        }
        pose.popMatrix();
        g.disableScissor();
        return hover;
    }

    private ItemStack renderNode(GuiGraphics g, Node node, int mx, int my) {
        int x = node.x;
        int y = node.y;
        g.fill(x, y, x + CARD_W, y + CARD_H, SURFACE);
        g.fill(x, y, x + CARD_W, y + 16, 0xff97b4bb);
        var view = node.view();
        var out = view.entry().output();
        g.drawString(font, font.plainSubstrByWidth(out.getHoverName().getString(), 115), x + 4, y + 4, INK, false);
        mark(g, "<", x + 4, y + 20, 16, 14);
        mark(g, (node.page + 1) + "/" + node.views.size(), x + 22, y + 20, 34, 14);
        mark(g, ">", x + 58, y + 20, 16, 14);
        String mode = node.choice.mode().name().toLowerCase(Locale.ROOT);
        mark(g, tr(view.entry().workbench() ? mode : "cooking").getString(), x + 76, y + 20, 65, 14);
        if (view.layout() != null && nativeLayoutAvailable()) {
            position(node);
            List<IRecipeSlotDrawable> overrides = displayBranchIngredients(node);
            view.layout().drawRecipe(g, mx, my);
            ItemStack hovered = view.layout().getItemStackUnderMouse(mx, my).orElse(ItemStack.EMPTY);
            overrides.forEach(IRecipeSlotDrawable::clearDisplayOverrides);
            return hovered;
        }
        var ingredients = view.entry().ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            var options = ingredients.get(i).getItems();
            if (options.length == 0) continue;
            int bx = x + 14 + (i % view.entry().width()) * 19;
            int by = y + 40 + (i / view.entry().width()) * 19;
            var branch = choices.get(BlueprintPlan.child(node.path, i));
            var item = branch == null ? options[ticks / 30 % options.length] : stack(branch.item());
            g.fill(bx, by, bx + 17, by + 17, 0xffa8bbc1);
            g.renderItem(item, bx, by);
            if (mx >= bx && mx < bx + 17 && my >= by && my < by + 17) return item;
        }
        return ItemStack.EMPTY;
    }

    private List<IRecipeSlotDrawable> displayBranchIngredients(Node node) {
        if (node.children.isEmpty()) return List.of();
        var slots = inputSlots(node.view().layout());
        var changed = new ArrayList<IRecipeSlotDrawable>(node.children.size());
        for (var child : node.children) {
            int index = child.path.charAt(child.path.length() - 1) - '0';
            int ordinal = ingredientOrdinal(node.view().entry(), slots, index);
            if (ordinal >= 0 && ordinal < slots.size() && slots.get(ordinal) instanceof IRecipeSlotDrawable slot) {
                slot.createDisplayOverrides().add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack(child.choice.item()));
                changed.add(slot);
            }
        }
        return changed;
    }

    private static List<IRecipeSlotView> inputSlots(mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout) {
        return layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(slot -> !slot.isEmpty()).toList();
    }

    private static int ingredientOrdinal(BlueprintRecipes.Entry entry, List<IRecipeSlotView> slots, int index) {
        if (slots.size() == entry.ingredients().size()) return index;
        int ordinal = 0;
        for (int i = 0; i < index; i++) if (!entry.ingredients().get(i).isEmpty()) ordinal++;
        return ordinal;
    }

    private void position(Node node) {
        var layout = node.view().layout();
        int width = layout.getRect().getWidth();
        int height = layout.getRect().getHeight();
        layout.setPosition(node.x + (CARD_W - width) / 2, node.y + 36 + Math.max(0, (64 - height) / 2));
    }

    private void mark(GuiGraphics g, String label, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xffc4d4d4);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, INK, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (mouseX >= divider() || mouseY < TOP) return false;
        if (choosing && button == 0) {
            int col = (int) (mouseX - 8) / 24;
            int row = (int) (mouseY - TOP - 23) / 24;
            int cols = Math.max(1, (divider() - 17) / 24);
            int index = (searchScroll + row) * cols + col;
            if (mouseX >= 8 && mouseY >= TOP + 23 && col < cols && index >= 0 && index < results.size()
                    && (mouseX - 8) % 24 < 20 && (mouseY - TOP - 23) % 24 < 20) {
                selectItem(results.get(index));
                return true;
            }
        }
        if (choosing) return false;
        int x = canvasX(mouseX);
        int y = canvasY(mouseY);
        if (button == 0) {
            for (var node : List.copyOf(placed)) {
                if (x < node.x || x >= node.x + CARD_W || y < node.y || y >= node.y + CARD_H) continue;
                int mx = x - node.x;
                int my = y - node.y;
                if (my >= 20 && my < 34) {
                    if (mx < 21) rotate(node, -1);
                    else if (mx >= 58 && mx < 75) rotate(node, 1);
                    else if (mx >= 76 && node.view().entry().workbench()) {
                        var mode = BlueprintPlan.Mode.values()[(node.choice.mode().ordinal() + 1) % BlueprintPlan.Mode.values().length];
                        choices.put(node.path, new BlueprintPlan.Choice(node.choice.item(), node.view().entry().id(), mode));
                        rebuild();
                    }
                } else if (my >= 36) {
                    expandAt(node, x, y);
                }
                return true;
            }
        }
        panning = true;
        return true;
    }

    private void rotate(Node node, int delta) {
        int next = Math.floorMod(node.page + delta, node.views.size());
        if (next == node.page) return;
        choices.keySet().removeIf(path -> path.startsWith(node.path + "/"));
        var view = node.views.get(next);
        var mode = view.entry().workbench() ? node.choice.mode() : BlueprintPlan.Mode.LOOSE;
        choices.put(node.path, new BlueprintPlan.Choice(node.choice.item(), view.entry().id(), mode));
        rebuild();
    }

    private void expandAt(Node node, int x, int y) {
        var entry = node.view().entry();
        ItemStack item;
        int slot = -1;
        if (node.view().layout() != null && nativeLayoutAvailable()) {
            position(node);
            var layout = node.view().layout();
            var hit = layout.getSlotUnderMouse(x, y).map(mezz.jei.api.gui.inputs.RecipeSlotUnderMouse::slot).orElse(null);
            if (hit == null || hit.getRole() != RecipeIngredientRole.INPUT) return;
            item = hit.getDisplayedItemStack().orElse(ItemStack.EMPTY);
            var slots = inputSlots(layout);
            int index = slots.indexOf(hit);
            if (index < 0) return;
            for (int i = 0; i < entry.ingredients().size(); i++) {
                if (!entry.ingredients().get(i).isEmpty() && ingredientOrdinal(entry, slots, i) == index) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0 || !entry.ingredients().get(slot).test(item)) {
                slot = -1;
                for (int i = 0; i < entry.ingredients().size(); i++) if (entry.ingredients().get(i).test(item)) { slot = i; break; }
            }
        } else {
            int px = x - node.x - 14;
            int py = y - node.y - 40;
            if (px < 0 || py < 0 || px / 19 >= entry.width()) return;
            slot = py / 19 * entry.width() + px / 19;
            if (slot >= entry.ingredients().size()) return;
            var options = entry.ingredients().get(slot).getItems();
            item = options.length == 0 ? ItemStack.EMPTY : options[ticks / 30 % options.length];
        }
        if (slot < 0 || item.isEmpty()) return;
        String next = BlueprintPlan.child(node.path, slot);
        if (choices.containsKey(next)) {
            choices.keySet().removeIf(path -> path.equals(next) || path.startsWith(next + "/"));
            rebuild();
            return;
        }
        Identifier childItem = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (!canExpand(node.path, childItem) || next.length() > BlueprintPlan.MAX_DEPTH * 2
                || choices.size() >= BlueprintPlan.MAX_NODES) return;
        var options = views(childItem);
        if (!options.isEmpty()) {
            choices.put(next, new BlueprintPlan.Choice(childItem, options.getFirst().entry().id(), BlueprintPlan.Mode.ANY));
            rebuild();
        }
    }

    private boolean canExpand(String path, Identifier item) {
        while (true) {
            var node = choices.get(path);
            if (node != null && node.item().equals(item)) return false;
            if (path.isEmpty()) return true;
            path = path.substring(0, path.lastIndexOf('/'));
        }
    }

    private int canvasX(double x) { return Mth.floor((x - panX) / zoom); }
    private int canvasY(double y) { return Mth.floor((y - TOP - panY) / zoom); }

    private static boolean inside(Rect2i rect, int x, int y) {
        return x >= rect.getX() && y >= rect.getY() && x < rect.getX() + rect.getWidth() && y < rect.getY() + rect.getHeight();
    }

    private static ItemStack stack(Identifier id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(id)); }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        int button = click.button();
        if (panning) {
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        int button = click.button();
        if (panning) {
            panning = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= editorWidth() || y < TOP) return false;
        if (x >= divider()) {
            totalsScroll = Mth.clamp(totalsScroll - (int) Math.signum(vertical), 0,
                    Math.max(0, materialRows.size() - Math.max(1, (height - TOP - 25) / 20)));
        } else if (choosing) {
            int cols = Math.max(1, (divider() - 17) / 24);
            searchScroll = Mth.clamp(searchScroll - (int) Math.signum(vertical), 0,
                    Math.max(0, (results.size() + cols - 1) / cols - Math.max(1, (height - TOP - 31) / 24)));
        } else {
            double cx = (x - panX) / zoom;
            double cy = (y - TOP - panY) / zoom;
            zoom = Mth.clamp((float) (zoom * Math.pow(1.14, vertical)), .08F, 2.3F);
            panX = x - cx * zoom;
            panY = y - TOP - cy * zoom;
        }
        return true;
    }
}


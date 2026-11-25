package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import java.util.LinkedHashMap;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.BlueprintRecipes;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels;

@JeiPlugin
public final class WeaselJei implements IModPlugin {
    private static IJeiRuntime jei;
    private static @Nullable InventoryScreen dragScreen;
    private static boolean inventoryDragRegistered;
    private static ItemStack dragged = ItemStack.EMPTY;

    static @Nullable IJeiRuntime runtime() { return jei; }

    @Override
    public ResourceLocation getPluginUid() { return SnowcacheWeasels.id("recipe_blueprint"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jei = runtime;
        if (!inventoryDragRegistered) {
            var bus = MinecraftForge.EVENT_BUS;
            bus.addListener(EventPriority.HIGHEST, InventoryDrag::start);
            bus.addListener(EventPriority.HIGHEST, InventoryDrag::drop);
            bus.addListener(InventoryDrag::preview);
            bus.addListener(EventPriority.HIGHEST, WeaselJei::closeBlueprintAtShutdown);
            inventoryDragRegistered = true;
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        jei = null;
        resetDrag();
        var game = Minecraft.getInstance();
        game.execute(() -> {
            if (game.screen instanceof BlueprintScreen) game.setScreen(null);
        });
    }

    private static void closeBlueprintAtShutdown(GameShuttingDownEvent event) {
        var game = Minecraft.getInstance();
        if (game.isSameThread() && game.screen instanceof BlueprintScreen) {
            // JEI tears down its overlay before the final rendered frame.
            game.setScreen(null);
        }
    }

    record View(BlueprintRecipes.Entry entry, @Nullable IRecipeLayoutDrawable<?> layout) {}

    static List<View> recipes(ResourceLocation output, BlueprintRecipes known) {
        if (jei == null) return List.of();
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(output));
        if (stack.isEmpty()) return List.of();
        var manager = jei.getRecipeManager();
        var focusFactory = jei.getJeiHelpers().getFocusFactory();
        var focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        var group = focusFactory.createFocusGroup(List.of(focus));
        var recipes = new LinkedHashMap<ResourceLocation, View>();
        manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                .forEach(category -> addViews(category, focus, group, output, known, recipes));
        // Data-pack recipes can be present before JEI finishes refreshing the ingredient list.
        for (var entry : known.forOutput(output)) recipes.putIfAbsent(entry.id(), new View(entry, null));
        return List.copyOf(recipes.values());
    }

    private static <T> void addViews(IRecipeCategory<T> category, IFocus<ItemStack> focus, IFocusGroup group,
                                     ResourceLocation output, BlueprintRecipes known, LinkedHashMap<ResourceLocation, View> views) {
        var manager = jei.getRecipeManager();
        manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().forEach(recipe -> {
            if (!(recipe instanceof Recipe<?> holder)) return;
            var entry = known.byId(holder.getId());
            if (entry == null || !BuiltInRegistries.ITEM.getKey(entry.output().getItem()).equals(output)) return;
            var layout = manager.createRecipeLayoutDrawable(category, recipe, group).orElse(null);
            views.putIfAbsent(entry.id(), new View(entry, layout));
        });
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration gui) {
        gui.addGuiScreenHandler(BlueprintScreen.class, screen -> new IGuiProperties() {
            @Override public Class<? extends Screen> getScreenClass() { return BlueprintScreen.class; }
            @Override public int getGuiLeft() { return 0; }
            @Override public int getGuiTop() { return 0; }
            @Override public int getGuiXSize() { return screen.width > 1 ? screen.editorWidth() : BlueprintScreen.editorWidth(getScreenWidth()); }
            @Override public int getGuiYSize() { return getScreenHeight(); }
            @Override public int getScreenWidth() { return Math.max(screen.width, Minecraft.getInstance().getWindow().getGuiScaledWidth()); }
            @Override public int getScreenHeight() { return Math.max(screen.height, Minecraft.getInstance().getWindow().getGuiScaledHeight()); }
        });
        gui.addGhostIngredientHandler(BlueprintScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(BlueprintScreen screen, ITypedIngredient<I> ingredient, boolean start) {
                var stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) return List.of();
                return List.of(new Target<>() {
                    @Override public Rect2i getArea() { return screen.dropTarget(); }
                    @Override public void accept(I value) { screen.selectItem(stack); }
                });
            }
            @Override public void onComplete() {}
        });
    }

    private static void resetDrag() {
        dragScreen = null;
        dragged = ItemStack.EMPTY;
    }

    private static List<net.minecraft.world.inventory.Slot> blueprintSlots(InventoryScreen screen) {
        return screen.getMenu().slots.stream()
                .filter(slot -> slot.container instanceof Inventory && slot.getItem().is(SnowcacheWeasels.BLUEPRINT.get()))
                .toList();
    }

    private static final class InventoryDrag {
        private InventoryDrag() {}

        // JEI reserves the vanilla inventory screen. Only intercept drops on a blueprint slot.
        public static void start(ScreenEvent.MouseButtonPressed.Pre event) {
            resetDrag();
            if (event.getButton() != 0 || jei == null || !(event.getScreen() instanceof InventoryScreen screen)
                    || blueprintSlots(screen).isEmpty()) return;
            var stack = jei.getIngredientListOverlay().getIngredientUnderMouse()
                    .flatMap(item -> item.getIngredient(VanillaTypes.ITEM_STACK)).orElse(ItemStack.EMPTY);
            var level = Minecraft.getInstance().level;
            if (stack.isEmpty() || level == null || level.getRecipeManager().getRecipes().stream().noneMatch(
                    recipe -> recipe.getResultItem(level.registryAccess()).is(stack.getItem()))) return;
            dragged = stack.copyWithCount(1);
            dragScreen = screen;
        }

        public static void drop(ScreenEvent.MouseButtonReleased.Pre event) {
            if (event.getButton() != 0) return;
            InventoryScreen screen = dragScreen;
            ItemStack stack = dragged;
            resetDrag();
            if (screen == null || screen != event.getScreen() || stack.isEmpty()) return;
            for (var slot : blueprintSlots(screen)) {
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                if (event.getMouseX() >= x && event.getMouseX() < x + 16
                        && event.getMouseY() >= y && event.getMouseY() < y + 16) {
                    var tag = new CompoundTag();
                    tag.putInt("DragSlot", slot.getContainerSlot());
                    tag.putString("Output", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    WeaselClient.request(tag);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        public static void preview(ScreenEvent.Render.Post event) {
            InventoryScreen screen = dragScreen;
            if (screen == null) return;
            if (screen != event.getScreen()) {
                resetDrag();
                return;
            }
            var graphics = event.getGuiGraphics();
            for (var slot : blueprintSlots(screen)) {
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                graphics.renderOutline(x - 1, y - 1, 18, 18, 0xff7fc7dc);
            }
            graphics.renderFakeItem(dragged, event.getMouseX() + 6, event.getMouseY() + 6);
        }
    }
}

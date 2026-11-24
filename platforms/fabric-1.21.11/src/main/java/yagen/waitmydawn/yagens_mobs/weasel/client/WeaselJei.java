package yagen.waitmydawn.yagens_mobs.weasel.client;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.weasel.BlueprintRecipes;
import yagen.waitmydawn.yagens_mobs.weasel.SnowcacheWeasels;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
import yagen.waitmydawn.yagens_mobs.weasel.mixin.WeaselInventoryPosition;

@JeiPlugin
public final class WeaselJei implements IModPlugin {
    private static IJeiRuntime jei;
    private static @Nullable InventoryScreen dragScreen;
    private static boolean inventoryDragRegistered;
    private static ItemStack dragged = ItemStack.EMPTY;

    static @Nullable IJeiRuntime runtime() { return jei; }

    @Override
    public Identifier getPluginUid() { return SnowcacheWeasels.id("recipe_blueprint"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jei = runtime;
        clientRecipes = null;
        if (!inventoryDragRegistered) {
            ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
                if (screen instanceof InventoryScreen) {
                    ScreenMouseEvents.beforeMouseClick(screen).register((view, click) -> InventoryDrag.start(view, click.x(), click.y(), click.button()));
                    ScreenMouseEvents.allowMouseRelease(screen).register((view, click) -> InventoryDrag.drop(view, click.x(), click.y(), click.button()));
                    ScreenEvents.afterRender(screen).register((view, graphics, x, y, delta) ->
                            InventoryDrag.preview(view, graphics, x, y));
                }
            });
            ClientLifecycleEvents.CLIENT_STOPPING.register(WeaselJei::closeBlueprintAtShutdown);
            inventoryDragRegistered = true;
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        jei = null;
        clientRecipes = null;
        resetDrag();
        var game = Minecraft.getInstance();
        game.execute(() -> {
            if (game.screen instanceof BlueprintScreen) game.setScreen(null);
        });
    }

    private static void closeBlueprintAtShutdown(Minecraft game) {
        if (game.isSameThread() && game.screen instanceof BlueprintScreen) {
            // JEI tears down its overlay before the final rendered frame.
            game.setScreen(null);
        }
    }

    private static BlueprintRecipes clientRecipes;

    static BlueprintRecipes catalog(net.minecraft.client.multiplayer.ClientLevel level) {
        if (clientRecipes == null) {
            var holders = new LinkedHashMap<Identifier, RecipeHolder<?>>();
            if (jei != null) {
                var manager = jei.getRecipeManager();
                manager.createRecipeCategoryLookup().get().forEach(category ->
                        manager.createRecipeLookup(category.getRecipeType()).get().forEach(recipe -> {
                            if (recipe instanceof RecipeHolder<?> holder) {
                                holders.putIfAbsent(holder.id().identifier(), holder);
                            }
                        }));
            }
            clientRecipes = new BlueprintRecipes(holders.values(), level);
        }
        return clientRecipes;
    }

    record View(BlueprintRecipes.Entry entry, @Nullable IRecipeLayoutDrawable<?> layout) {}

    static List<View> recipes(Identifier output, BlueprintRecipes known) {
        if (jei == null) return List.of();
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(output));
        if (stack.isEmpty()) return List.of();
        var manager = jei.getRecipeManager();
        var focusFactory = jei.getJeiHelpers().getFocusFactory();
        var focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        var group = focusFactory.createFocusGroup(List.of(focus));
        var recipes = new LinkedHashMap<Identifier, View>();
        manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                .forEach(category -> addViews(category, focus, group, output, known, recipes));
        // Data-pack recipes can be present before JEI finishes refreshing the ingredient list.
        for (var entry : known.forOutput(output)) recipes.putIfAbsent(entry.id(), new View(entry, null));
        return List.copyOf(recipes.values());
    }

    private static <T> void addViews(IRecipeCategory<T> category, IFocus<ItemStack> focus, IFocusGroup group,
                                     Identifier output, BlueprintRecipes known, LinkedHashMap<Identifier, View> views) {
        var manager = jei.getRecipeManager();
        manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().forEach(recipe -> {
            if (!(recipe instanceof RecipeHolder<?> holder)) return;
            var entry = known.byId(holder.id().identifier());
            if (entry == null || !BuiltInRegistries.ITEM.getKey(entry.output().getItem()).equals(output)) return;
            var layout = manager.createRecipeLayoutDrawable(category, recipe, group).orElse(null);
            views.putIfAbsent(entry.id(), new View(entry, layout));
        });
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration gui) {
        gui.addGuiScreenHandler(BlueprintScreen.class, screen -> new IGuiProperties() {
            @Override public Class<? extends Screen> screenClass() { return BlueprintScreen.class; }
            @Override public int guiLeft() { return 0; }
            @Override public int guiTop() { return 0; }
            @Override public int guiXSize() { return screen.width > 1 ? screen.editorWidth() : BlueprintScreen.editorWidth(screenWidth()); }
            @Override public int guiYSize() { return screenHeight(); }
            @Override public int screenWidth() { return Math.max(screen.width, Minecraft.getInstance().getWindow().getGuiScaledWidth()); }
            @Override public int screenHeight() { return Math.max(screen.height, Minecraft.getInstance().getWindow().getGuiScaledHeight()); }
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
        public static void start(Screen view, double mouseX, double mouseY, int button) {
            resetDrag();
            if (button != 0 || jei == null || !(view instanceof InventoryScreen screen)
                    || blueprintSlots(screen).isEmpty()) return;
            var stack = jei.getIngredientListOverlay().getIngredientUnderMouse()
                    .flatMap(item -> item.getIngredient(VanillaTypes.ITEM_STACK)).orElse(ItemStack.EMPTY);
            var level = Minecraft.getInstance().level;
            if (stack.isEmpty() || level == null || catalog(level).forOutput(BuiltInRegistries.ITEM.getKey(stack.getItem())).isEmpty()) return;
            dragged = stack.copyWithCount(1);
            dragScreen = screen;
        }

        public static boolean drop(Screen view, double mouseX, double mouseY, int button) {
            if (button != 0) return true;
            InventoryScreen screen = dragScreen;
            ItemStack stack = dragged;
            resetDrag();
            if (screen == null || screen != view || stack.isEmpty()) return true;
            for (var slot : blueprintSlots(screen)) {
                int x = ((WeaselInventoryPosition) screen).yagensMobs$left() + slot.x;
                int y = ((WeaselInventoryPosition) screen).yagensMobs$top() + slot.y;
                if (mouseX >= x && mouseX < x + 16
                        && mouseY >= y && mouseY < y + 16) {
                    var tag = new CompoundTag();
                    tag.putInt("DragSlot", slot.getContainerSlot());
                    tag.putString("Output", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    WeaselClient.request(tag);
                    return false;
                }
            }
            return true;
        }

        public static void preview(Screen view, GuiGraphics graphics, int mouseX, int mouseY) {
            InventoryScreen screen = dragScreen;
            if (screen == null) return;
            if (screen != view) {
                resetDrag();
                return;
            }
            for (var slot : blueprintSlots(screen)) {
                int x = ((WeaselInventoryPosition) screen).yagensMobs$left() + slot.x;
                int y = ((WeaselInventoryPosition) screen).yagensMobs$top() + slot.y;
                graphics.renderOutline(x - 1, y - 1, 18, 18, 0xff7fc7dc);
            }
            graphics.renderFakeItem(dragged, mouseX + 6, mouseY + 6);
        }
    }
}

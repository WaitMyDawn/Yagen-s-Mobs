package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

public final class BlueprintRecipes {
    private static final Identifier COOKING = Identifier.fromNamespaceAndPath("farmersdelight", "cooking");
    private static final Map<RecipeManager, BlueprintRecipes> SERVER = new WeakHashMap<>();

    public record Entry(RecipeHolder<?> holder, ItemStack output, List<BlueprintIngredient> ingredients, int width, int height,
                        boolean workbench) {
        public Identifier id() { return holder.id().identifier(); }
    }

    private final Map<Item, List<Entry>> byItem = new HashMap<>();
    private final Map<Identifier, Entry> byId = new HashMap<>();

    public BlueprintRecipes(Collection<RecipeHolder<?>> recipes, net.minecraft.world.level.Level level) {
        for (RecipeHolder<?> candidate : recipes) {
            Entry entry = entry(candidate, level);
            if (entry != null) {
                byItem.computeIfAbsent(entry.output().getItem(), ignored -> new ArrayList<>()).add(entry);
                byId.put(entry.id(), entry);
            }
        }
        for (var list : byItem.values()) {
            list.sort(Comparator.comparing((Entry entry) -> !entry.workbench()).thenComparing(Entry::id));
        }
    }

    public static @Nullable Entry entry(RecipeHolder<?> holder, net.minecraft.world.level.Level level) {
        var recipe = holder.value();
        boolean workbench = recipe instanceof CraftingRecipe;
        if ((!workbench && !COOKING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) || recipe.isSpecial()) return null;
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        var result = recipe.display().stream().map(display -> display.result().resolveForFirstStack(context))
                .filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        var placement = recipe.placementInfo();
        var ingredients = new ArrayList<BlueprintIngredient>();
        if (!placement.isImpossibleToPlace()) {
            placement.slotsToIngredientIndex().forEach((int index) -> ingredients.add(new BlueprintIngredient(
                    index < 0 ? null : placement.ingredients().get(index))));
        } else if (!workbench) {
            readCookingRecipe(recipe, ingredients);
        }
        if (!workbench) {
            var container = cookingContainer(recipe);
            if (!container.isEmpty() && ingredients.stream().noneMatch(ingredient -> ingredient.test(container))) ingredients.add(new BlueprintIngredient(net.minecraft.world.item.crafting.Ingredient.of(container.getItem())));
        }
        if (result.isEmpty() || ingredients.isEmpty() || ingredients.size() > 9) return null;
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (ingredients.size() + 2) / 3;
        return new Entry(holder, result.copy(), List.copyOf(ingredients), width, height, workbench);
    }

    private static void readCookingRecipe(Object recipe, List<BlueprintIngredient> ingredients) {
        try {
            var inputs = recipe.getClass().getMethod("getIngredients").invoke(recipe);
            if (inputs instanceof Iterable<?> list) {
                for (var input : list) {
                    if (input instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                        ingredients.add(new BlueprintIngredient(ingredient));
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Cooking integrations may expose placement information without a legacy ingredient list.
        }
    }

    private static ItemStack cookingContainer(Object recipe) {
        try {
            return recipe.getClass().getMethod("getOutputContainer").invoke(recipe) instanceof ItemStack stack
                    ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    static BlueprintRecipes get(ServerLevel level) {
        return SERVER.computeIfAbsent(level.recipeAccess(), manager -> new BlueprintRecipes(manager.getRecipes(), level));
    }

    static void invalidate() { SERVER.clear(); }

    public List<Entry> forOutput(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? List.of() : byItem.getOrDefault(item, List.of());
    }

    public @Nullable Entry byId(@Nullable Identifier id) { return id == null ? null : byId.get(id); }

    public @Nullable Entry select(BlueprintPlan.Choice choice) {
        if (choice.recipe() == null) return forOutput(choice.item()).stream().findFirst().orElse(null);
        var entry = byId(choice.recipe());
        return entry != null && BuiltInRegistries.ITEM.getKey(entry.output().getItem()).equals(choice.item()) ? entry : null;
    }

    boolean valid(BlueprintPlan plan) {
        if (select(plan.root()) == null) return false;
        for (var pair : plan.choices().entrySet()) {
            if (select(pair.getValue()) == null) return false;
            if (pair.getKey().isEmpty()) continue;
            int last = pair.getKey().length() - 1;
            String parentPath = pair.getKey().substring(0, last - 1);
            var parent = parentPath.isEmpty() ? plan.root() : plan.choices().get(parentPath);
            if (parent == null || plan.root().item().equals(pair.getValue().item())) return false;
            for (int split = parentPath.length(); split > 0; split -= 2) {
                var ancestor = plan.choices().get(parentPath.substring(0, split));
                if (ancestor != null && ancestor.item().equals(pair.getValue().item())) return false;
            }
            var entry = select(parent);
            int slot = pair.getKey().charAt(last) - '0';
            if (entry == null || slot >= entry.ingredients().size()
                    || !entry.ingredients().get(slot).test(new ItemStack(BuiltInRegistries.ITEM.getValue(pair.getValue().item())))) return false;
        }
        return true;
    }
}


package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class BlueprintPlanner {
    public record Materials(Map<Identifier, Long> direct, Map<Identifier, Long> expanded) {}

    private record Branch(String path, BlueprintPlan.Choice choice) {}
    private record Requirement(BlueprintIngredient ingredient, @Nullable Branch child) {}
    record Batch(Identifier recipe, long count, boolean root) {}
    record Result(WeaselStock pulls, List<Batch> batches, boolean shortage) {}

    private final BlueprintPlan plan;
    private final BlueprintRecipes recipes;
    private final WeaselStock available;
    private final WeaselStock simulated = new WeaselStock();
    private final WeaselStock pulls = new WeaselStock();
    private final List<Batch> batches = new ArrayList<>();
    private boolean shortage;
    private @Nullable Map<Identifier, Long> direct;
    private @Nullable Map<Identifier, Long> expanded;

    BlueprintPlanner(BlueprintPlan plan, BlueprintRecipes recipes, WeaselStock available) {
        this.plan = plan;
        this.recipes = recipes;
        this.available = available;
    }

    Result prepare() {
        expand(plan.root(), "", plan.quantity(), new HashSet<>());
        return new Result(pulls, List.copyOf(batches), shortage);
    }

    public static Materials materials(BlueprintPlan plan, BlueprintRecipes recipes) {
        var planner = new BlueprintPlanner(plan, recipes, new WeaselStock());
        var direct = new LinkedHashMap<Identifier, Long>();
        var expanded = new LinkedHashMap<Identifier, Long>();
        planner.direct = direct;
        planner.expanded = expanded;
        planner.expand(plan.root(), "", plan.quantity(), new HashSet<>());
        return new Materials(Collections.unmodifiableMap(direct), Collections.unmodifiableMap(expanded));
    }

    private void expand(BlueprintPlan.Choice choice, String path, long count, Set<Identifier> branch) {
        var selected = recipes.select(choice);
        if (selected == null || !branch.add(choice.item())) {
            shortage = true;
            return;
        }
        var entry = expanded == null ? choose(choice, selected, count) : selected;
        long runs = (count + entry.output().getCount() - 1) / entry.output().getCount();
        var requirements = requirements(path, selected, entry);
        var ordered = new ArrayList<>(requirements.keySet());
        ordered.sort(Comparator.comparingInt(input -> input.ingredient().getItems().length));

        // Reserve immediate inputs together before letting any descendant consume the remaining stock.
        var missing = new LinkedHashMap<Requirement, Long>();
        for (var requirement : ordered) {
            long needed = runs * requirements.get(requirement);
            if (path.isEmpty() && direct != null) listMaterial(direct, requirement, needed);
            missing.put(requirement, needed - reserve(requirement.ingredient(), needed, childChoice(requirement)));
        }

        long completed = runs;
        for (var requirement : ordered) {
            int copies = requirements.get(requirement);
            long absent = missing.get(requirement);
            absent -= reserve(requirement.ingredient(), absent, childChoice(requirement));
            if (absent > 0) {
                var child = requirement.child();
                if (child != null && !branch.contains(child.choice().item())) {
                    expand(child.choice(), child.path(), absent, branch);
                    absent -= reserve(requirement.ingredient(), absent, child.choice());
                } else if (expanded != null) {
                    listMaterial(expanded, requirement, absent);
                    absent = 0;
                }
            }
            completed = Math.min(completed, (runs * copies - absent) / copies);
        }

        boolean craft = choice.mode() != BlueprintPlan.Mode.LOOSE && entry.workbench();
        if (expanded != null || craft) {
            if (expanded == null) batches.add(new Batch(entry.id(), runs, path.isEmpty()));
            simulated.add(entry.output(), completed * entry.output().getCount());
        }
        shortage |= completed < runs;
        branch.remove(choice.item());
    }

    private Map<Requirement, Integer> requirements(String path, BlueprintRecipes.Entry selected, BlueprintRecipes.Entry entry) {
        var branches = new LinkedHashMap<BlueprintIngredient, List<Branch>>();
        for (int slot = 0; slot < selected.ingredients().size(); slot++) {
            var child = branchAt(path, slot);
            if (child != null) {
                branches.computeIfAbsent(selected.ingredients().get(slot), ignored -> new ArrayList<>()).add(child);
            }
        }
        var requirements = new LinkedHashMap<Requirement, Integer>();
        boolean usesSelectedRecipe = entry.id().equals(selected.id());
        for (int slot = 0; slot < entry.ingredients().size(); slot++) {
            BlueprintIngredient ingredient = entry.ingredients().get(slot);
            if (ingredient.isEmpty()) continue;
            Branch child = usesSelectedRecipe ? branchAt(path, slot) : null;
            if (child == null) {
                List<Branch> equivalent = branches.get(ingredient);
                if (equivalent == null && !usesSelectedRecipe) {
                    equivalent = branches.values().stream().flatMap(List::stream).filter(candidate ->
                            ingredient.test(new ItemStack(BuiltInRegistries.ITEM.getValue(candidate.choice().item())))).toList();
                }
                child = sharedChoice(equivalent);
            }
            requirements.merge(new Requirement(ingredient, child), 1, Integer::sum);
        }
        return requirements;
    }

    private @Nullable Branch branchAt(String path, int slot) {
        String childPath = BlueprintPlan.child(path, slot);
        var choice = plan.choices().get(childPath);
        return choice == null ? null : new Branch(childPath, choice);
    }

    private static @Nullable Branch sharedChoice(@Nullable List<Branch> branches) {
        if (branches == null || branches.isEmpty()) return null;
        var choice = branches.getFirst();
        return branches.stream().allMatch(branch -> branch.choice().equals(choice.choice())) ? choice : null;
    }

    private static @Nullable BlueprintPlan.Choice childChoice(Requirement requirement) {
        return requirement.child() == null ? null : requirement.child().choice();
    }

    private static void listMaterial(Map<Identifier, Long> list, Requirement requirement, long count) {
        var child = childChoice(requirement);
        var stacks = requirement.ingredient().getItems();
        if (child != null) {
            list.merge(child.item(), count, Long::sum);
        } else if (stacks.length > 0) {
            list.merge(BuiltInRegistries.ITEM.getKey(stacks[0].getItem()), count, Long::sum);
        }
    }

    private BlueprintRecipes.Entry choose(BlueprintPlan.Choice choice, BlueprintRecipes.Entry selected, long count) {
        if (choice.mode() != BlueprintPlan.Mode.ANY || !selected.workbench()) {
            return selected;
        }
        var best = selected;
        long bestCoverage = coverage(selected, count);
        for (var candidate : recipes.forOutput(choice.item())) {
            if (!candidate.workbench() || candidate == selected) continue;
            long score = coverage(candidate, count);
            if (score > bestCoverage) {
                best = candidate;
                bestCoverage = score;
            }
        }
        return best;
    }

    private long coverage(BlueprintRecipes.Entry entry, long count) {
        long requested = (count + entry.output().getCount() - 1) / entry.output().getCount();
        WeaselStock reserved = new WeaselStock();
        long found = 0;
        int inputs = 0;
        List<BlueprintIngredient> sorted = entry.ingredients().stream().filter(part -> !part.isEmpty())
                .sorted(Comparator.comparingInt(part -> part.getItems().length)).toList();
        for (BlueprintIngredient part : sorted) {
            inputs++;
            long wanted = requested;
            for (Item item : items(part)) {
                for (ItemStack key : candidates(item)) {
                    if (wanted == 0) break;
                    if (part.test(key)) {
                        long accepted = Math.min(wanted, Math.max(0, available.count(key) + simulated.count(key) - reserved.count(key)));
                        found += accepted;
                        wanted -= accepted;
                        reserved.add(key, accepted);
                    }
                }
            }
        }
        return inputs == 0 ? 0 : Math.min(count, found * entry.output().getCount() / inputs);
    }

    private List<ItemStack> candidates(Item item) {
        List<ItemStack> keys = new ArrayList<>(available.variants(item));
        for (var stack : simulated.variants(item)) {
            if (available.count(stack) == 0) keys.add(stack);
        }
        return keys;
    }

    private long reserve(BlueprintIngredient ingredient, long count, @Nullable BlueprintPlan.Choice child) {
        long remaining = count;
        List<Item> items = items(ingredient);
        if (child != null) {
            Item preferred = BuiltInRegistries.ITEM.getValue(child.item());
            if (items.remove(preferred)) items.addFirst(preferred);
        }
        for (Item item : items) {
            for (ItemStack stack : simulated.variants(item)) {
                if (remaining == 0) return count;
                if (ingredient.test(stack)) remaining -= simulated.remove(stack, remaining);
            }
        }
        for (Item item : items) {
            for (ItemStack stack : available.variants(item)) {
                if (remaining == 0) return count;
                if (ingredient.test(stack)) {
                    long taken = available.remove(stack, remaining);
                    pulls.add(stack, taken);
                    remaining -= taken;
                }
            }
        }
        return count - remaining;
    }

    static List<Item> items(BlueprintIngredient ingredient) {
        var types = new LinkedHashSet<Item>();
        Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).forEach(stack -> types.add(stack.getItem()));
        return new ArrayList<>(types);
    }
}


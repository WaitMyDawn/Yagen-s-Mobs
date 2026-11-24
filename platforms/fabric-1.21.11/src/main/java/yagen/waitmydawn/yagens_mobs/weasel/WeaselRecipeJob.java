package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

final class WeaselRecipeJob {
    private final BlueprintPlan plan;
    private final WeaselStock stock = new WeaselStock();
    private WeaselStock pulls = new WeaselStock();
    private final List<BlueprintPlanner.Batch> batches = new ArrayList<>();
    private long crafted;
    private boolean started;
    private boolean shortage;

    WeaselRecipeJob(BlueprintPlan plan) {
        this.plan = plan;
    }

    boolean started() { return started; }
    WeaselStock stock() { return stock; }
    ItemStack display() { return stock.display(); }

    void prepare(ServerLevel level, List<WeaselChests.Site> sites, Function<BlockPos, WeaselChests.Mode> mode) {
        if (started) return;
        started = true;
        var recipes = BlueprintRecipes.get(level);
        if (!recipes.valid(plan)) {
            shortage = true;
            return;
        }
        WeaselStock available = new WeaselStock();
        for (var site : sites) {
            if (!mode.apply(site.pos()).take()) continue;
            for (int slot = 0; slot < site.items().getContainerSize(); slot++) {
                available.add(site.items().getItem(slot));
            }
        }
        var prepared = new BlueprintPlanner(plan, recipes, available).prepare();
        pulls = prepared.pulls();
        batches.addAll(prepared.batches());
        shortage = prepared.shortage();
    }

    boolean canCollect(WeaselChests.Site site) {
        if (pulls.isEmpty()) return false;
        for (int slot = 0; slot < site.items().getContainerSize(); slot++) {
            if (pulls.count(site.items().getItem(slot)) > 0) return true;
        }
        return false;
    }

    boolean collect(WeaselChests.Site site) {
        boolean moved = false;
        for (int slot = 0; slot < site.items().getContainerSize(); slot++) {
            var stack = site.items().getItem(slot);
            int amount = (int) Math.min(stack.getCount(), pulls.count(stack));
            if (amount <= 0) continue;
            ItemStack taken = site.items().removeItem(slot, amount);
            if (!taken.isEmpty()) {
                pulls.remove(taken, taken.getCount());
                stock.add(taken);
                moved = true;
            }
        }
        return moved;
    }

    private @Nullable CraftingInput input(BlueprintRecipes.Entry entry, ServerLevel level) {
        var inputs = entry.ingredients();
        var resolved = new ArrayList<List<ItemStack>>(inputs.size());
        for (BlueprintIngredient ingredient : inputs) {
            if (ingredient.isEmpty()) {
                resolved.add(List.of(ItemStack.EMPTY));
                continue;
            }
            var options = BlueprintPlanner.items(ingredient).stream().flatMap(item -> stock.variants(item).stream())
                    .filter(ingredient::test).sorted(java.util.Comparator.<ItemStack>comparingLong(stock::count).reversed()).toList();
            if (options.isEmpty()) return null;
            resolved.add(options);
        }
        Integer[] order = new Integer[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) order[i] = i;
        Arrays.sort(order, java.util.Comparator.comparingInt(slot -> resolved.get(slot).size()));
        ItemStack[] slots = new ItemStack[entry.width() * entry.height()];
        Arrays.fill(slots, ItemStack.EMPTY);
        return match(entry, level, resolved, order, slots, 0, new WeaselStock(), new int[]{512});
    }

    private @Nullable CraftingInput match(BlueprintRecipes.Entry entry, ServerLevel level, List<List<ItemStack>> options,
                                          Integer[] order, ItemStack[] slots, int depth, WeaselStock used, int[] budget) {
        if (budget[0]-- <= 0) return null;
        if (depth == order.length) {
            var input = CraftingInput.of(entry.width(), entry.height(), Arrays.asList(slots.clone()));
            return ((CraftingRecipe) entry.holder().value()).matches(input, level) ? input : null;
        }
        int slot = order[depth];
        for (ItemStack stack : options.get(slot)) {
            if (!stack.isEmpty() && used.count(stack) >= stock.count(stack)) continue;
            used.add(stack, 1);
            slots[slot] = stack.copy();
            var chosen = match(entry, level, options, order, slots, depth + 1, used, budget);
            used.remove(stack, 1);
            if (chosen != null) return chosen;
            if (budget[0] <= 0) break;
        }
        return null;
    }

    boolean canCraft(ServerLevel level) {
        while (!batches.isEmpty()) {
            var entry = BlueprintRecipes.get(level).byId(batches.getFirst().recipe());
            if (entry != null && entry.workbench() && input(entry, level) != null) return true;
            batches.removeFirst();
            shortage = true;
        }
        return false;
    }

    void craft(ServerLevel level) {
        // Reactivate the workbench animation for another bounded batch if the job has more work.
        int steps = 0;
        while (steps++ < 32 && canCraft(level)) {
            var batch = batches.getFirst();
            var entry = BlueprintRecipes.get(level).byId(batch.recipe());
            var recipe = (CraftingRecipe) entry.holder().value();
            var input = input(entry, level);
            if (input == null) return;
            ItemStack output = WeaselPlatform.assemble(recipe, input, level);
            if (output.isEmpty() || output.getItem() != entry.output().getItem()) {
                batches.removeFirst();
                shortage = true;
                continue;
            }
            var remainder = recipe.getRemainingItems(input);
            var ingredients = new WeaselStock();
            var unique = new ArrayList<ItemStack>();
            for (ItemStack stack : input.items()) {
                if (!stack.isEmpty()) {
                    if (ingredients.count(stack) == 0) unique.add(stack);
                    ingredients.add(stack, 1);
                }
            }
            long runs = 1;
            if (recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class) {
                runs = batch.count();
                for (ItemStack stack : unique) runs = Math.min(runs, stock.count(stack) / ingredients.count(stack));
            }
            if (runs <= 0) return;
            for (ItemStack stack : unique) stock.remove(stack, ingredients.count(stack) * runs);
            stock.add(output, output.getCount() * runs);
            for (ItemStack stack : remainder) stock.add(stack, stack.getCount() * runs);
            if (batch.root()) crafted += output.getCount() * runs;
            if (runs == batch.count()) {
                batches.removeFirst();
            } else {
                batches.set(0, new BlueprintPlanner.Batch(batch.recipe(), batch.count() - runs, batch.root()));
            }
        }
    }

    boolean complete() {
        return crafted >= plan.quantity() || batches.isEmpty() && pulls.isEmpty() && !shortage;
    }

    CompoundTag save(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        tag.put("Plan", plan.save());
        tag.put("Bag", stock.save(lookup));
        tag.put("Pulls", pulls.save(lookup));
        tag.putBoolean("Started", started);
        tag.putBoolean("Short", shortage);
        tag.putLong("Crafted", crafted);
        var tasks = new ListTag();
        for (var batch : batches) {
            var entry = new CompoundTag();
            entry.putString("Recipe", batch.recipe().toString());
            entry.putLong("Runs", batch.count());
            entry.putBoolean("Root", batch.root());
            tasks.add(entry);
        }
        tag.put("Batches", tasks);
        return tag;
    }

    static @Nullable WeaselRecipeJob load(HolderLookup.Provider lookup, CompoundTag tag) {
        var plan = BlueprintPlan.load(tag.getCompoundOrEmpty("Plan"));
        if (plan == null) return null;
        var job = new WeaselRecipeJob(plan);
        job.started = tag.getBooleanOr("Started", false);
        job.shortage = tag.getBooleanOr("Short", false);
        job.crafted = tag.getLongOr("Crafted", 0L);
        job.stock.read(lookup, tag.getListOrEmpty("Bag"));
        job.pulls.read(lookup, tag.getListOrEmpty("Pulls"));
        for (Tag value : tag.getListOrEmpty("Batches")) {
            if (!(value instanceof CompoundTag row)) continue;
            var id = Identifier.tryParse(row.getStringOr("Recipe", ""));
            if (id != null && row.getLongOr("Runs", 0L) > 0) {
                job.batches.add(new BlueprintPlanner.Batch(id, row.getLongOr("Runs", 0L), row.getBooleanOr("Root", false)));
            }
        }
        return job;
    }
}


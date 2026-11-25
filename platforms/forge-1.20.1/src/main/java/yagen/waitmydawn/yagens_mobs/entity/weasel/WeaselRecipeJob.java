package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

final class WeaselRecipeJob {
    private static final AbstractContainerMenu INPUT_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

        @Override
        public boolean stillValid(Player player) { return false; }
    };

    private final BlueprintPlan plan;
    private final WeaselStock stock = new WeaselStock();
    private WeaselStock pulls = new WeaselStock();
    private final List<BlueprintPlanner.Batch> batches = new ArrayList<>();
    private long crafted;
    private boolean started;
    private boolean shortage;
    private boolean craftingStarted;

    WeaselRecipeJob(BlueprintPlan plan) {
        this.plan = plan;
    }

    boolean started() { return started; }
    WeaselStock stock() { return stock; }
    ItemStack display() { return stock.display(); }

    void prepare(ServerLevel level, List<WeaselChests.Site> sites, SnowcacheWeasel mob) {
        if (started) return;
        started = true;
        planMaterials(level, sites, mob);
    }

    void refreshMaterials(ServerLevel level, List<WeaselChests.Site> sites, SnowcacheWeasel mob) {
        // Replanning is only used before any workbench batches have run.
        if (!craftingStarted) planMaterials(level, sites, mob);
    }

    private void planMaterials(ServerLevel level, List<WeaselChests.Site> sites, SnowcacheWeasel mob) {
        var recipes = BlueprintRecipes.get(level);
        if (!recipes.valid(plan)) {
            shortage = true;
            return;
        }
        var found = new WeaselStock();
        // Existing job stock is already physically carried and must not be pulled a second time.
        for (ItemStack key : stock.keys()) found.add(key, stock.count(key));
        Set<Item> relevant = new HashSet<>();
        if (sites.stream().anyMatch(WeaselChests.Site::routed)) collectRelevantItems(recipes, relevant);
        var groups = new LinkedHashMap<Object, WeaselStock>();
        for (var site : sites) {
            if (!site.mode(mob).take()) continue;
            var endpoint = site.endpoint();
            if (!site.routed() && endpoint.group().equals(site.pos())) {
                site.items().addStock(found, relevant);
                continue;
            }
            var key = endpoint.buffer() == null ? endpoint.group() : endpoint.buffer();
            var aggregate = groups.computeIfAbsent(key, ignored -> new WeaselStock());
            var snapshot = new WeaselStock();
            site.items().addStock(snapshot, relevant);
            // Multiple ports can expose different filters over the same stock. Count each visible variant once.
            for (var stack : snapshot.keys()) aggregate.add(stack, snapshot.count(stack) - aggregate.count(stack));
        }
        groups.values().forEach(stock -> stock.moveTo(found));
        var prepared = new BlueprintPlanner(plan, recipes, found).prepare();
        pulls = prepared.pulls();
        for (ItemStack key : stock.keys()) pulls.remove(key, stock.count(key));
        batches.clear();
        batches.addAll(prepared.batches());
        shortage = prepared.shortage();
    }

    private void collectRelevantItems(BlueprintRecipes recipes, Set<Item> items) {
        var choices = new HashSet<>(plan.choices().values());
        choices.add(plan.root());
        for (var choice : choices) {
            var entries = choice.mode() == BlueprintPlan.Mode.ANY ? recipes.forOutput(choice.item())
                    : java.util.stream.Stream.ofNullable(recipes.select(choice)).toList();
            for (var entry : entries) {
                for (var ingredient : entry.ingredients()) items.addAll(BlueprintPlanner.items(ingredient));
            }
        }
    }

    boolean canCollect(WeaselChests.Site site) {
        return !pulls.isEmpty() && site.items().hasMaterial(pulls);
    }

    boolean collect(WeaselChests.Site site) {
        return site.items().collect(pulls, stock);
    }

    boolean needsMaterials() { return !craftingStarted && (shortage || !pulls.isEmpty()); }

    private @Nullable CraftingContainer input(BlueprintRecipes.Entry entry, ServerLevel level) {
        var inputs = entry.ingredients();
        var resolved = new ArrayList<List<ItemStack>>(inputs.size());
        for (Ingredient ingredient : inputs) {
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

    private @Nullable CraftingContainer match(BlueprintRecipes.Entry entry, ServerLevel level, List<List<ItemStack>> options,
                                          Integer[] order, ItemStack[] slots, int depth, WeaselStock used, int[] budget) {
        if (budget[0]-- <= 0) return null;
        if (depth == order.length) {
            var input = new TransientCraftingContainer(INPUT_MENU, entry.width(), entry.height());
            for (int index = 0; index < slots.length; index++) input.setItem(index, slots[index].copy());
            return ((CraftingRecipe) entry.holder()).matches(input, level) ? input : null;
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
            var entry = BlueprintRecipes.get(level).byId(batches.get(0).recipe());
            if (entry != null && entry.workbench() && input(entry, level) != null) return true;
            batches.remove(0);
            shortage = true;
        }
        return false;
    }

    void craft(ServerLevel level) {
        craftingStarted = true;
        // Reactivate the workbench animation for another bounded batch if the job has more work.
        int steps = 0;
        while (steps++ < 32 && canCraft(level)) {
            var batch = batches.get(0);
            var entry = BlueprintRecipes.get(level).byId(batch.recipe());
            var recipe = (CraftingRecipe) entry.holder();
            var input = input(entry, level);
            if (input == null) return;
            ItemStack output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty() || output.getItem() != entry.output().getItem()) {
                batches.remove(0);
                shortage = true;
                continue;
            }
            var remainder = recipe.getRemainingItems(input);
            var ingredients = new WeaselStock();
            var unique = new ArrayList<ItemStack>();
            for (int slot = 0; slot < input.getContainerSize(); slot++) {
                ItemStack stack = input.getItem(slot);
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
                batches.remove(0);
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
        tag.putBoolean("CraftingStarted", craftingStarted);
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
        var plan = BlueprintPlan.load(tag.getCompound("Plan"));
        if (plan == null) return null;
        var job = new WeaselRecipeJob(plan);
        job.started = tag.getBoolean("Started");
        job.shortage = tag.getBoolean("Short");
        job.crafted = tag.getLong("Crafted");
        job.craftingStarted = job.crafted > 0 || tag.getBoolean("CraftingStarted");
        job.stock.read(lookup, tag.getList("Bag", Tag.TAG_COMPOUND));
        job.pulls.read(lookup, tag.getList("Pulls", Tag.TAG_COMPOUND));
        for (Tag value : tag.getList("Batches", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            var id = ResourceLocation.tryParse(row.getString("Recipe"));
            if (id != null && row.getLong("Runs") > 0) {
                job.batches.add(new BlueprintPlanner.Batch(id, row.getLong("Runs"), row.getBoolean("Root")));
            }
        }
        return job;
    }
}


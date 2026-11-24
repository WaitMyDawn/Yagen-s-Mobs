package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class WeaselWork {
    private static final int FULL_SCAN_INTERVAL = 100;
    private static final int IDLE_INTERVAL = 20;
    private static final int CONTACT_TICKS = 22;
    private static final int ACTION_TIMEOUT = 200;
    private static final double INTERACTION_DISTANCE_SQUARED = 7.3;
    private static final int RECIPE_RETRY_LIMIT = 600;
    private final SnowcacheWeasel mob;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private List<BlockPos> positions = List.of();
    private long nextPlan;
    private long nextDiscovery;
    private long lastRevision = -1;
    private long timeout;
    private int interaction;
    private BlockPos lastWorkbench;
    private long nextBenchSearch;
    private long benchRetryDeadline;
    private long materialsRetryDeadline;
    private Action action;

    private enum Operation { SORT_PICK, SORT_STORE, UNLOAD, MATERIALS, CRAFT }

    private record Action(BlockPos pos, Operation op, @Nullable Identifier tab, boolean fallback) {}

    private record WorkbenchSearch(@Nullable BlockPos ready, long retryAt) {}

    WeaselWork(SnowcacheWeasel mob) {
        this.mob = mob;
    }

    void reset() {
        nextPlan = 0;
        nextDiscovery = 0;
        lastRevision = -1;
        lastWorkbench = null;
        nextBenchSearch = 0;
        benchRetryDeadline = 0;
        materialsRetryDeadline = 0;
        action = null;
        interaction = 0;
        unreachable.clear();
        mob.getNavigation().stop();
        mob.setWorkPose(SnowcacheWeasel.Pose.NORMAL);
    }

    boolean active() {
        return action != null;
    }

    void cancel(ServerLevel level) {
        if (action != null && interaction > 0 && action.op() != Operation.CRAFT) {
            lid(level, action.pos(), false);
        }
        reset();
    }

    void tick(ServerLevel level, ServerPlayer owner) {
        if (action != null) {
            advance(level, owner);
            return;
        }
        long now = level.getGameTime();
        if (now < nextPlan || now % 5 != mob.getId() % 5) {
            return;
        }
        WeaselTabs.prepare(level);
        BlockPos center = mob.workCenter();
        boolean rediscover = now >= nextDiscovery;
        if (rediscover) {
            positions = WeaselChests.discover(level, center, owner);
            nextDiscovery = now + FULL_SCAN_INTERVAL;
            unreachable.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        long revision = WeaselChestRevisions.since(level, center, WeaselChests.RANGE);
        if (!rediscover && mob.recipeJob() == null && mob.cargo().isEmpty() && revision == lastRevision) {
            nextPlan = now + IDLE_INTERVAL;
            return;
        }
        List<WeaselChests.Site> sites = WeaselChests.resolve(level, positions, owner);
        lastRevision = revision;
        Action next = choose(sites, level, owner, now);
        if (next == null) {
            nextPlan = Math.max(nextPlan, now + IDLE_INTERVAL);
            return;
        }
        if (next.op() != Operation.CRAFT || !atWorkbench(next.pos())) {
            if (!mob.onGround() && !mob.isInLiquid()) {
                // Ground navigation cannot build a path while the weasel is airborne.
                nextPlan = now + 5;
                return;
            }
            Set<BlockPos> approaches = new HashSet<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = next.pos().relative(side);
                for (int vertical = -1; vertical <= 1; vertical++) {
                    BlockPos foot = adjacent.offset(0, vertical, 0);
                    if (level.hasChunkAt(foot) && level.getBlockState(foot).getCollisionShape(level, foot).isEmpty()) {
                        approaches.add(foot);
                    }
                }
            }
            if (!approaches.contains(mob.blockPosition())) {
                var navigation = mob.getNavigation();
                if (navigation.isDone()) {
                    // A completed path with identical nodes would be retained by moveTo.
                    navigation.stop();
                }
                var path = navigation.createPath(approaches, 0);
                if (path == null || !path.canReach() || !navigation.moveTo(path, 1.2)) {
                    backOff(next, now, FULL_SCAN_INTERVAL);
                    nextPlan = now + 5;
                    return;
                }
            }
        }
        action = next;
        timeout = now + ACTION_TIMEOUT;
    }

    private boolean reachable(BlockPos pos, long time) {
        return unreachable.getOrDefault(pos, 0L) <= time;
    }

    private @Nullable Action choose(List<WeaselChests.Site> sites, ServerLevel level, ServerPlayer owner, long now) {
        if (mob.recipeJob() != null) {
            return recipeAction(sites, level, owner, now);
        }
        var reachableSites = sites.stream().filter(site -> reachable(site.pos(), now)).toList();
        if (!mob.cargo().isEmpty()) {
            return storage(reachableSites, mob.cargo(), false);
        }
        Map<Identifier, List<WeaselChests.Site>> targets = new HashMap<>();
        for (var site : reachableSites) {
            if (mob.chestMode(site.pos()).deposit()) {
                targets.computeIfAbsent(site.category(), ignored -> new ArrayList<>()).add(site);
            }
        }
        for (var site : reachableSites) {
            var mode = mob.chestMode(site.pos());
            int first = site.first();
            if (!mode.take() || first < 0) {
                continue;
            }
            Identifier anchorTab = site.category();
            for (int slot = mode == WeaselChests.Mode.TAKE ? first : first + 1; slot < site.items().getContainerSize(); slot++) {
                ItemStack stack = site.items().getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                Identifier tab = WeaselTabs.of(stack);
                if (mode != WeaselChests.Mode.TAKE && tab.equals(anchorTab)) {
                    continue;
                }
                if (hasMatching(targets.get(tab), site, stack) || hasMatching(targets.get(null), site, stack)
                        || mode == WeaselChests.Mode.TAKE && reachableSites.stream().anyMatch(target ->
                            !target.pos().equals(site.pos()) && mob.chestMode(target.pos()).deposit() && target.room(stack, true) > 0)) {
                    return new Action(site.pos(), Operation.SORT_PICK, tab, false);
                }
            }
        }
        return null;
    }

    private @Nullable Action recipeAction(List<WeaselChests.Site> sites, ServerLevel level, ServerPlayer owner, long now) {
        var recipe = mob.recipeJob();
        if (!recipe.started()) {
            if (!mob.pendingUnload().isEmpty()) {
                // Check one stack beyond double-chest capacity so oversized loads cannot appear to fit.
                var reachableSites = sites.stream().filter(site -> reachable(site.pos(), now)).toList();
                return storage(reachableSites, mob.pendingUnload().preview(55), true);
            }
            recipe.prepare(level, sites, mob::chestMode);
        }
        long retryMaterialsAt = Long.MAX_VALUE;
        for (var site : sites) {
            if (!mob.chestMode(site.pos()).take() || !recipe.canCollect(site)) {
                continue;
            }
            if (reachable(site.pos(), now)) {
                return new Action(site.pos(), Operation.MATERIALS, null, false);
            }
            retryMaterialsAt = Math.min(retryMaterialsAt, unreachable.get(site.pos()));
        }
        boolean materialsBlocked = retryMaterialsAt != Long.MAX_VALUE;
        if (materialsBlocked) {
            if (materialsRetryDeadline == 0) {
                materialsRetryDeadline = now + RECIPE_RETRY_LIMIT;
            }
            if (now < materialsRetryDeadline) {
                // The stock is present. Keep the pull plan while the affected path is in backoff.
                nextPlan = Math.min(retryMaterialsAt, materialsRetryDeadline);
                return null;
            }
        }
        if (recipe.canCraft(level)) {
            WorkbenchSearch bench = findWorkbench(level, mob.workCenter(), owner, now);
            if (bench.ready() != null) {
                return new Action(bench.ready(), Operation.CRAFT, null, false);
            }
            if (bench.retryAt() != Long.MAX_VALUE) {
                startWorkbenchRetry(now);
                if (now < benchRetryDeadline) {
                    nextPlan = Math.min(bench.retryAt(), benchRetryDeadline);
                    return null;
                }
            }
            mob.tell("no_workbench");
        }
        mob.finishRecipe(materialsBlocked);
        benchRetryDeadline = 0;
        materialsRetryDeadline = 0;
        return null;
    }

    private WorkbenchSearch findWorkbench(ServerLevel level, BlockPos center, ServerPlayer owner, long now) {
        if (lastWorkbench != null && usableWorkbench(level, owner, lastWorkbench) && reachable(lastWorkbench, now)) {
            return new WorkbenchSearch(lastWorkbench, now);
        }
        if (now < nextBenchSearch) return new WorkbenchSearch(null, nextBenchSearch);
        nextBenchSearch = now + IDLE_INTERVAL;

        long retryAt = lastWorkbench != null && !level.hasChunkAt(lastWorkbench)
                ? now + FULL_SCAN_INTERVAL : Long.MAX_VALUE;
        BlockPos nearest = null;
        int range = WeaselChests.RANGE;
        double best = range * range + .5;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            double distance = pos.distSqr(center);
            if (distance >= best || !usableWorkbench(level, owner, pos)) continue;
            if (reachable(pos, now)) {
                nearest = pos.immutable();
                best = distance;
            } else {
                retryAt = Math.min(retryAt, unreachable.get(pos));
            }
        }
        if (nearest != null) lastWorkbench = nearest;
        return new WorkbenchSearch(nearest, retryAt);
    }

    private static boolean usableWorkbench(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) && level.mayInteract(owner, pos);
    }

    private boolean atWorkbench(BlockPos pos) {
        return mob.blockPosition().equals(pos.above());
    }

    private boolean withinInteractionRange(BlockPos pos) {
        return mob.distanceToSqr(Vec3.atCenterOf(pos)) <= INTERACTION_DISTANCE_SQUARED;
    }

    private void startWorkbenchRetry(long now) {
        if (benchRetryDeadline == 0) benchRetryDeadline = now + RECIPE_RETRY_LIMIT;
    }

    private void backOff(Action task, long now, int duration) {
        unreachable.put(task.pos(), now + duration);
        if (task.op() == Operation.CRAFT) {
            startWorkbenchRetry(now);
            nextBenchSearch = 0;
        }
    }

    private static boolean hasMatching(@Nullable List<WeaselChests.Site> candidates, WeaselChests.Site source, ItemStack stack) {
        return candidates != null && candidates.stream().anyMatch(site -> !site.pos().equals(source.pos()) && site.room(stack, false) > 0);
    }

    private @Nullable Action storage(List<WeaselChests.Site> sites, List<ItemStack> cargo, boolean unload) {
        Operation op = unload ? Operation.UNLOAD : Operation.SORT_STORE;
        Identifier desired = WeaselTabs.of(cargo.getFirst());
        WeaselChests.Site matching = null;
        WeaselChests.Site empty = null;
        WeaselChests.Site enough = null;
        WeaselChests.Site partial = null;
        WeaselChests.Site source = null;
        var ordered = sites.stream().sorted(Comparator.comparingDouble(
                (WeaselChests.Site site) -> mob.distanceToSqr(Vec3.atCenterOf(site.pos())))).toList();
        for (var site : ordered) {
            if (!mob.chestMode(site.pos()).deposit()) {
                continue;
            }
            if (site.pos().equals(mob.lastPickup())) {
                source = site;
                continue;
            }
            var tab = site.category();
            if (!unload && matching == null && desired.equals(tab)
                    && cargo.stream().anyMatch(stack -> site.room(stack, false) > 0)) {
                matching = site;
            } else if (!unload && empty == null && tab == null
                    && cargo.stream().anyMatch(stack -> site.room(stack, false) > 0)) {
                empty = site;
            }
            if (enough == null && site.fitsAtEnd(cargo)) {
                enough = site;
            }
            if (partial == null && cargo.stream().anyMatch(stack -> site.room(stack, true) > 0)) {
                partial = site;
            }
        }
        if (matching != null) {
            return new Action(matching.pos(), op, desired, false);
        }
        if (empty != null) {
            return new Action(empty.pos(), op, desired, false);
        }
        var fallback = enough == null ? partial : enough;
        if (fallback == null && source != null && source.fitsAtEnd(cargo)) {
            fallback = source;
        }
        return fallback == null ? null : new Action(fallback.pos(), op, desired, true);
    }

    private void advance(ServerLevel level, ServerPlayer owner) {
        var task = action;
        boolean crafting = task.op() == Operation.CRAFT;
        if (level.getGameTime() >= timeout || !level.hasChunkAt(task.pos())
                || crafting && !usableWorkbench(level, owner, task.pos())) {
            abandon(level, task);
            return;
        }
        if (interaction == 0) {
            if (!mob.getNavigation().isDone()) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(task.pos());
            if (!withinInteractionRange(task.pos())) {
                abandon(level, task);
                return;
            }
            double face = Math.atan2(center.z - mob.getZ(), center.x - mob.getX());
            mob.setYRot((float) (Math.toDegrees(face) - 90));
            mob.setYHeadRot(mob.getYRot());
            mob.setYBodyRot(mob.getYRot());
            if (crafting) {
                BlockPos above = task.pos().above();
                Vec3 standing = Vec3.atBottomCenterOf(above);
                if (!level.noCollision(mob, mob.getBoundingBox().move(standing.subtract(mob.position())))) {
                    abandon(level, task);
                    return;
                }
                mob.snapTo(standing.x, standing.y, standing.z, mob.getYRot(), 0);
            } else {
                lid(level, task.pos(), true);
            }
            mob.setWorkPose(switch (task.op()) {
                case CRAFT -> SnowcacheWeasel.Pose.CRAFT;
                case SORT_PICK, MATERIALS -> SnowcacheWeasel.Pose.SEARCH;
                default -> SnowcacheWeasel.Pose.STORE;
            });
            interaction = 1;
            return;
        }
        interaction++;
        if (interaction == switch (task.op()) {
            case SORT_PICK, MATERIALS -> 37;
            case CRAFT, UNLOAD, SORT_STORE -> CONTACT_TICKS;
        }) {
            if (!transfer(level, owner, task)) {
                if (crafting) {
                    abandon(level, task);
                    return;
                }
                backOff(task, level.getGameTime(), IDLE_INTERVAL);
            }
        }
        int length = switch (task.op()) {
            case SORT_PICK, MATERIALS -> 61;
            case CRAFT -> 38;
            default -> 42;
        };
        if (interaction >= length) {
            if (crafting) {
                var job = mob.recipeJob();
                // Keep the station and animation while actual crafting work is ready at this table.
                if (withinInteractionRange(task.pos()) && job != null && job.canCraft(level)) {
                    interaction = 1;
                    timeout = level.getGameTime() + ACTION_TIMEOUT;
                    return;
                }
            } else {
                lid(level, task.pos(), false);
            }
            action = null;
            interaction = 0;
            nextPlan = level.getGameTime() + 5;
            mob.setWorkPose(SnowcacheWeasel.Pose.NORMAL);
        }
    }

    private boolean transfer(ServerLevel level, ServerPlayer owner, Action task) {
        if (task.op() == Operation.CRAFT) {
            var job = mob.recipeJob();
            if (job == null || !withinInteractionRange(task.pos()) || !usableWorkbench(level, owner, task.pos())) {
                return false;
            }
            job.craft(level);
            benchRetryDeadline = 0;
            return true;
        }
        var site = WeaselChests.at(level, task.pos(), owner);
        if (site == null || !site.pos().equals(task.pos()) || !withinInteractionRange(task.pos())) {
            return false;
        }
        var mode = mob.chestMode(site.pos());
        switch (task.op()) {
            case SORT_PICK -> {
                if (mode.take() && mob.cargo().isEmpty()) {
                    int first = site.first();
                    if (mode != WeaselChests.Mode.TAKE && Objects.equals(site.category(), task.tab())) {
                        return false;
                    }
                    for (int slot = Math.max(0, first + (mode == WeaselChests.Mode.TAKE ? 0 : 1));
                         slot < site.items().getContainerSize() && mob.cargo().size() < 9; slot++) {
                        var stack = site.items().getItem(slot);
                        if (!stack.isEmpty() && WeaselTabs.of(stack).equals(task.tab())) {
                            mob.cargo().add(site.items().removeItem(slot, stack.getCount()));
                        }
                    }
                    if (!mob.cargo().isEmpty()) {
                        mob.setLastPickup(site.pos());
                        mob.refreshCarried();
                        return true;
                    }
                }
            }
            case SORT_STORE, UNLOAD -> {
                if (mode.deposit() && (task.fallback() || site.category() == null || site.category().equals(task.tab()))) {
                    boolean unloading = task.op() == Operation.UNLOAD;
                    var cargo = unloading ? mob.pendingUnload().preview(site.items().getContainerSize()) : mob.cargo();
                    boolean changed = false;
                    for (var stack : cargo) {
                        var key = unloading ? stack.copyWithCount(1) : ItemStack.EMPTY;
                        int inserted = site.deposit(stack, task.fallback());
                        if (unloading && inserted > 0) mob.pendingUnload().remove(key, inserted);
                        changed |= inserted > 0;
                    }
                    if (!unloading) cargo.removeIf(ItemStack::isEmpty);
                    mob.refreshCarried();
                    return changed;
                }
            }
            case MATERIALS -> {
                if (mode.take() && mob.recipeJob() != null) {
                    boolean changed = mob.recipeJob().collect(site);
                    mob.refreshCarried();
                    return changed;
                }
            }
            default -> { }
        }
        return false;
    }

    private void abandon(ServerLevel level, Action task) {
        if (interaction > 0 && task.op() != Operation.CRAFT) {
            lid(level, task.pos(), false);
        }
        backOff(task, level.getGameTime(), FULL_SCAN_INTERVAL);
        mob.getNavigation().stop();
        action = null;
        interaction = 0;
        nextPlan = level.getGameTime() + 10;
        mob.setWorkPose(SnowcacheWeasel.Pose.NORMAL);
    }

    private static void lid(ServerLevel level, BlockPos pos, boolean open) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) {
            return;
        }
        if (open || ChestBlockEntity.getOpenCount(level, pos) == 0) {
            level.blockEvent(pos, chest, 1, open ? 1 : 0);
        }
        if (state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(other) && level.getBlockState(other).is(chest)
                    && (open || ChestBlockEntity.getOpenCount(level, other) == 0)) {
                level.blockEvent(other, chest, 1, open ? 1 : 0);
            }
        }
    }
}


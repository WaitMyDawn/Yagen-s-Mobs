package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

public final class SnowcacheWeasel extends TamableAnimal implements GeoEntity {
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SnowcacheWeasel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE =
            SynchedEntityData.defineId(SnowcacheWeasel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> HELD =
            SynchedEntityData.defineId(SnowcacheWeasel.class, EntityDataSerializers.ITEM_STACK);
    private static final String[] COLORS = {"blue", "light_blue", "light_pink", "white"};
    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation CARRY = loop("carry");
    private static final RawAnimation SEARCH = once("chest_search");
    private static final RawAnimation STORE = once("chest_deposit");
    private static final RawAnimation CRAFT = loop("workbench_craft");
    private static final RawAnimation SHOULDER = RawAnimation.begin().thenPlay(animation("shoulder_hop")).thenLoop(animation("shoulder_perch"));
    private static final RawAnimation PERCH = loop("shoulder_perch");
    private static final RawAnimation SNIFF = once("sniff");

    enum Pose { NORMAL, SEARCH, STORE, CRAFT, SHOULDER, SNIFF }

    private record ChestKey(Identifier dimension, BlockPos pos) {}

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    private final WeaselWork work = new WeaselWork(this);
    private final Map<ChestKey, WeaselChests.Mode> modes = new HashMap<>();
    private final Map<ChestKey, Integer> storagePriorities = new HashMap<>();
    private final Map<java.util.UUID, WeaselChests.Mode> backpackModes = new HashMap<>();
    private boolean routedPickup;
    private final List<ItemStack> cargo = new ArrayList<>(9);
    private final WeaselStock unload = new WeaselStock();
    private final WeaselStock delivery = new WeaselStock();
    private @Nullable WeaselRecipeJob recipeJob;
    private @Nullable BlockPos center;
    private @Nullable BlockPos lastPickup;
    private int sniffTime;

    public SnowcacheWeasel(EntityType<? extends SnowcacheWeasel> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, .28)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    static boolean checkSpawn(EntityType<SnowcacheWeasel> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return (reason != EntitySpawnReason.NATURAL || ServerConfigs.SNOWCACHE_WEASEL.canSpawn())
                && (checkAnimalSpawnRules(type, level, reason, pos, random)
                || ((level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                     || level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SNOW))
                    && level.getRawBrightness(pos, 0) > 8));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(2, new BreedGoal(this, 1));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1) {
            @Override
            public boolean canUse() { return !isTame() && super.canUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6) {
            @Override
            public boolean canUse() { return !work.active() && super.canUse(); }
            @Override
            public boolean canContinueToUse() { return !work.active() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() { return !work.active() && super.canUse(); }
            @Override
            public boolean canContinueToUse() { return !work.active() && super.canContinueToUse(); }
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR, 3);
        builder.define(POSE, Pose.NORMAL.ordinal());
        builder.define(HELD, ItemStack.EMPTY);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                         @Nullable SpawnGroupData group) {
        setColor(random.nextInt(COLORS.length));
        return super.finalizeSpawn(level, difficulty, reason, group == null
                ? new AgeableMob.AgeableMobGroupData(.2F) : group);
    }

    @Override
    public float getAgeScale() { return isBaby() ? .73F : 1F; }

    public String color() { return COLORS[entityData.get(COLOR)]; }

    public void setColor(int index) {
        entityData.set(COLOR, net.minecraft.util.Mth.clamp(index, 0, COLORS.length - 1));
    }

    public ItemStack mouthItem() { return entityData.get(HELD); }

    List<ItemStack> cargo() { return cargo; }
    WeaselStock pendingUnload() { return unload; }
    @Nullable WeaselRecipeJob recipeJob() { return recipeJob; }
    @Nullable BlockPos lastPickup() { return lastPickup; }
    boolean routedPickup() { return routedPickup; }
    void setLastPickup(@Nullable BlockPos pos, boolean routed) {
        lastPickup = pos;
        routedPickup = routed;
    }

    BlockPos workCenter() { return center == null ? blockPosition() : center; }

    WeaselChests.Mode chestMode(BlockPos pos) {
        return modes.getOrDefault(new ChestKey(level().dimension().identifier(), pos), WeaselChests.Mode.BOTH);
    }

    WeaselChests.Mode storageMode(WeaselChests.Site site, WeaselChests.Mode fallback) {
        var bag = site.endpoint().bag();
        if (bag != null) return backpackModes.getOrDefault(bag, fallback);
        return modes.getOrDefault(new ChestKey(level().dimension().identifier(), site.pos()), fallback);
    }

    void cycleStorage(WeaselChests.Site site) {
        var bag = site.endpoint().bag();
        var next = site.mode(this).next();
        if (bag != null) {
            if (next == WeaselChests.Mode.BOTH) backpackModes.remove(bag);
            else backpackModes.put(bag, next);
        } else {
            var key = new ChestKey(level().dimension().identifier(), site.pos());
            var fallback = site.routed() ? WeaselChests.Mode.TAKE : WeaselChests.Mode.BOTH;
            if (next == fallback) modes.remove(key);
            else modes.put(key, next);
        }
        tell("mode." + next.name().toLowerCase(java.util.Locale.ROOT));
    }

    int storagePriority(BlockPos pos, int fallback) {
        return storagePriorities.getOrDefault(new ChestKey(level().dimension().identifier(), pos), fallback);
    }

    void setStoragePriority(BlockPos pos, int priority, int fallback) {
        var key = new ChestKey(level().dimension().identifier(), pos.immutable());
        if (priority == fallback) storagePriorities.remove(key);
        else storagePriorities.put(key, priority);
    }

    void cycleChest(BlockPos pos) {
        var next = chestMode(pos).next();
        var key = new ChestKey(level().dimension().identifier(), pos.immutable());
        if (next == WeaselChests.Mode.BOTH) {
            modes.remove(key);
        } else {
            modes.put(key, next);
        }
        tell("mode." + next.name().toLowerCase(java.util.Locale.ROOT));
    }

    void setWorkPose(Pose pose) { entityData.set(POSE, pose.ordinal()); }

    void refreshCarried() {
        ItemStack display = !cargo.isEmpty() ? cargo.getFirst() : !unload.isEmpty() ? unload.display()
                : recipeJob != null ? recipeJob.display() : ItemStack.EMPTY;
        entityData.set(HELD, display.isEmpty() ? ItemStack.EMPTY : display.copyWithCount(1));
    }

    void leaveShoulder(Player player, boolean setCenter) {
        stopRiding();
        Vec3 point = player.position();
        snapTo(point.x, point.y, point.z, player.getYRot(), 0);
        if (setCenter) {
            center = blockPosition().immutable();
            if (player instanceof ServerPlayer carrier) {
                carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
            }
        }
        work.reset();
    }

    @Override
    public boolean isInWall() {
        // A head passenger can extend above a two-block doorway that fits the carrier.
        return (isBaby() || !(getVehicle() instanceof Player)) && super.isInWall();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (getVehicle() instanceof Player) {
            setWorkPose(Pose.SHOULDER);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (isTame() && !isBaby() && getOwner() instanceof ServerPlayer owner && !owner.isSpectator()) {
            if (center == null) {
                center = blockPosition().immutable();
            }
            if (!isInLove()) {
                work.tick(level, owner);
            } else if (work.active()) {
                work.cancel(level);
            }
        } else if (work.active()) {
            work.cancel(level);
        } else if (!isTame() && !getNavigation().isInProgress()) {
            if (sniffTime > 0 && --sniffTime == 0) {
                setWorkPose(Pose.NORMAL);
            } else if (sniffTime == 0 && tickCount % 20 == 0 && random.nextInt(12) == 0) {
                sniffTime = 37;
                setWorkPose(Pose.SNIFF);
            }
        }
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(Items.RABBIT) || stack.is(Items.CHICKEN); }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack item = player.getItemInHand(hand);
        if (isFood(item)) {
            if (!isTame()) {
                if (!level().isClientSide()) {
                    boolean rabbit = item.is(Items.RABBIT);
                    item.consume(1, player);
                    if ((rabbit || random.nextInt(3) == 0)
                            && SnowcacheWeasels.allowTame(this, player)) {
                        tame(player);
                        setPersistenceRequired();
                        center = blockPosition().immutable();
                        level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            var result = super.mobInteract(player, hand);
            if (result.consumesAction() && isInLove() && work.active() && level() instanceof ServerLevel server) {
                // Release work navigation before BreedGoal starts tracking the mate.
                work.cancel(server);
            }
            return result;
        }
        if (!isTame() || !isOwnedBy(player) || player.isSpectator()) {
            return super.mobInteract(player, hand);
        }
        if (item.is(SnowcacheWeasels.BLUEPRINT.get()) && !isBaby() && SnowcacheWeasels.blueprintsEnabled()) {
            if (player instanceof ServerPlayer owner) {
                acceptBlueprint(owner, item);
            }
            return InteractionResult.SUCCESS;
        }
        if (!item.isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer owner) {
                receiveCargo(owner);
            }
            return InteractionResult.SUCCESS;
        }
        if (!player.getPassengers().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer owner && startRiding(owner, true, true)) {
            work.cancel(owner.level());
            setWorkPose(Pose.SHOULDER);
            // Passenger changes on a player tracker are sent to observers, so the carrier needs its own update.
            owner.connection.send(new ClientboundSetPassengersPacket(owner));
            tell("shoulder");
        }
        return InteractionResult.SUCCESS;
    }

    private void acceptBlueprint(ServerPlayer owner, ItemStack item) {
        var plan = RecipeBlueprint.read(item);
        if (plan == null || !BlueprintRecipes.get(owner.level()).valid(plan)) {
            tell("invalid_blueprint");
            return;
        }
        if (recipeJob != null) {
            recipeJob.stock().moveTo(unload);
        }
        cargo.forEach(unload::add);
        cargo.clear();
        delivery.moveTo(unload);
        setLastPickup(null, false);
        recipeJob = new WeaselRecipeJob(plan);
        work.cancel(owner.level());
        refreshCarried();
        tell("collecting");
    }

    void finishRecipe(boolean materialsBlocked) {
        if (recipeJob != null) {
            if (recipeJob.stock().isEmpty()) {
                tell(materialsBlocked ? "materials_unreachable" : "materials_missing");
            } else {
                recipeJob.stock().moveTo(delivery);
                tell(recipeJob.complete() ? "complete" : "partial");
            }
            recipeJob = null;
            refreshCarried();
        }
    }

    private void receiveCargo(ServerPlayer player) {
        if (recipeJob != null) {
            tell("collecting");
            return;
        }
        work.cancel(player.level());
        setLastPickup(null, false);
        int transferred = delivery.give(player);
        transferred += unload.give(player);
        transferred += giveList(player, cargo);
        refreshCarried();
        if (transferred != 0) {
            player.sendOverlayMessage(Component.translatable("message.yagens_mobs.snowcache_weasel.retrieved", transferred));
        } else {
            tell(delivery.isEmpty() && unload.isEmpty() && cargo.isEmpty() ? "nothing" : "inventory_full");
        }
    }

    private static int giveList(ServerPlayer player, List<ItemStack> stacks) {
        int count = 0;
        var it = stacks.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            count += WeaselStock.giveStack(player, stack);
            if (stack.isEmpty()) {
                it.remove();
            }
        }
        return count;
    }

    void tell(String key) {
        if (getOwner() instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.yagens_mobs.snowcache_weasel." + key));
        }
    }

    private @Nullable java.util.UUID ownerUuid() {
        var reference = getOwnerReference();
        return reference == null ? null : reference.getUUID();
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        var child = SnowcacheWeasels.TYPE.get().create(level, EntitySpawnReason.BREEDING);
        if (child == null) {
            return null;
        }
        child.setColor(other instanceof SnowcacheWeasel mate && random.nextBoolean() ? mate.entityData.get(COLOR) : entityData.get(COLOR));
        if (other instanceof SnowcacheWeasel mate && ownerUuid() != null && ownerUuid().equals(mate.ownerUuid())) {
            child.setOwnerReference(net.minecraft.world.entity.EntityReference.of(ownerUuid()));
            child.setTame(true, false);
        }
        return child;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        work.cancel(level);
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (recipeJob != null) {
            recipeJob.stock().moveTo(delivery);
        }
        cargo.forEach(delivery::add);
        unload.moveTo(delivery);
        delivery.drop(level, this);
        cargo.clear();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        var tag = new CompoundTag();
        tag.putInt("WeaselColor", entityData.get(COLOR));
        if (center != null) tag.putLong("WorkCenter", center.asLong());
        if (lastPickup != null) tag.putLong("LastPickup", lastPickup.asLong());
        ListTag chestModes = new ListTag();
        for (var entry : modes.entrySet()) {
            var value = new CompoundTag();
            value.putString("Dimension", entry.getKey().dimension().toString());
            value.putLong("Pos", entry.getKey().pos().asLong());
            value.putInt("Mode", entry.getValue().ordinal());
            chestModes.add(value);
        }
        tag.put("ChestModes", chestModes);
        tag.putBoolean("RoutedPickup", routedPickup);
        var priorities = new ListTag();
        storagePriorities.forEach((key, priority) -> {
            var value = new CompoundTag();
            value.putString("Dimension", key.dimension().toString());
            value.putLong("Pos", key.pos().asLong());
            value.putInt("Priority", priority);
            priorities.add(value);
        });
        tag.put("StoragePriorities", priorities);
        var bags = new ListTag();
        backpackModes.forEach((bag, mode) -> {
            var value = new CompoundTag();
            value.putString("Bag", bag.toString());
            value.putInt("Mode", mode.ordinal());
            bags.add(value);
        });
        tag.put("BackpackModes", bags);
        writeStacks(tag, "WeaselCargo", cargo);
        tag.put("WeaselUnloadStock", unload.save(registryAccess()));
        tag.put("WeaselDelivery", delivery.save(registryAccess()));
        if (recipeJob != null) {
            tag.put("WeaselJob", recipeJob.save(registryAccess()));
        }
        yagen.waitmydawn.yagens_mobs.compat.SaveTag.write(output, tag);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        var tag = yagen.waitmydawn.yagens_mobs.compat.SaveTag.read(input);
        setColor(tag.getIntOr("WeaselColor", 0));
        center = tag.contains("WorkCenter") ? BlockPos.of(tag.getLongOr("WorkCenter", 0L)) : null;
        lastPickup = tag.contains("LastPickup") ? BlockPos.of(tag.getLongOr("LastPickup", 0L)) : null;
        modes.clear();
        for (Tag row : tag.getListOrEmpty("ChestModes")) {
            if (!(row instanceof CompoundTag value)) continue;
            int mode = value.getIntOr("Mode", 0);
            Identifier dimension = Identifier.tryParse(value.getStringOr("Dimension", ""));
            if (dimension != null && mode >= 0 && mode < WeaselChests.Mode.values().length) {
                modes.put(new ChestKey(dimension, BlockPos.of(value.getLongOr("Pos", 0L))), WeaselChests.Mode.values()[mode]);
            }
        }
        routedPickup = tag.getBooleanOr("RoutedPickup", false);
        storagePriorities.clear();
        for (Tag row : tag.getListOrEmpty("StoragePriorities")) {
            if (!(row instanceof CompoundTag value)) continue;
            var dimension = Identifier.tryParse(value.getStringOr("Dimension", ""));
            int priority = value.getIntOr("Priority", 100);
            if (dimension != null && priority >= 0 && priority <= 999) {
                storagePriorities.put(new ChestKey(dimension, BlockPos.of(value.getLongOr("Pos", 0L))), priority);
            }
        }
        backpackModes.clear();
        for (Tag row : tag.getListOrEmpty("BackpackModes")) {
            if (!(row instanceof CompoundTag value)) continue;
            int mode = value.getIntOr("Mode", 0);
            if (mode > 0 && mode < WeaselChests.Mode.values().length) {
                try {
                    backpackModes.put(java.util.UUID.fromString(value.getStringOr("Bag", "")), WeaselChests.Mode.values()[mode]);
                } catch (IllegalArgumentException ignored) {
                    // Invalid stored identifiers cannot refer to a placed backpack.
                }
            }
        }
        readStacks(tag, "WeaselCargo", cargo);
        unload.read(registryAccess(), tag.getListOrEmpty("WeaselUnloadStock"));
        delivery.read(registryAccess(), tag.getListOrEmpty("WeaselDelivery"));
        recipeJob = tag.contains("WeaselJob") ? WeaselRecipeJob.load(registryAccess(), tag.getCompoundOrEmpty("WeaselJob")) : null;
        refreshCarried();
    }

    private void writeStacks(CompoundTag tag, String key, List<ItemStack> stacks) {
        ListTag contents = new ListTag();
        stacks.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> contents.add(WeaselStock.saveStack(registryAccess(), stack)));
        tag.put(key, contents);
    }

    private void readStacks(CompoundTag tag, String key, List<ItemStack> stacks) {
        stacks.clear();
        for (Tag row : tag.getListOrEmpty(key)) {
            WeaselStock.readStack(registryAccess(), row).ifPresent(stacks::add);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar manager) {
        manager.add(new AnimationController<>("activity", 3, state -> {
            RawAnimation clip = switch (Pose.values()[entityData.get(POSE)]) {
                case SEARCH -> SEARCH;
                case STORE -> STORE;
                case CRAFT -> CRAFT;
                case SHOULDER -> tickCount <= 1 ? PERCH : SHOULDER;
                case SNIFF -> SNIFF;
                case NORMAL -> state.isMoving()
                        ? !mouthItem().isEmpty() ? CARRY : walkAnimation.speed() > .58F ? RUN : WALK
                        : IDLE;
            };
            return state.setAndContinue(clip);
        }));
    }

    private static String animation(String clip) { return "animation.snowcache_weasel." + clip; }
    private static RawAnimation loop(String clip) { return RawAnimation.begin().thenLoop(animation(clip)); }
    private static RawAnimation once(String clip) { return RawAnimation.begin().thenPlayAndHold(animation(clip)); }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animations; }
}


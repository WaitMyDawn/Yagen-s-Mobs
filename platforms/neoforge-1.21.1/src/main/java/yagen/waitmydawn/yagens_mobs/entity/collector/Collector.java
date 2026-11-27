package yagen.waitmydawn.yagens_mobs.entity.collector;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class Collector extends PathfinderMob implements GeoEntity {
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path); }
    public enum Coat {
        CHERRY, CAVE, SNOW, SWAMP, DESERT, PRAIRIE;
        public final String key = name().toLowerCase(java.util.Locale.ROOT);
        final TagKey<Biome> habitats = TagKey.create(Registries.BIOME, id("collector/" + key));
        static Coat parse(String name) {
            for (Coat coat : values()) if (coat.key.equals(name)) return coat;
            return PRAIRIE;
        }
    }
    private static final EntityDataAccessor<String> COAT = SynchedEntityData.defineId(Collector.class, EntityDataSerializers.STRING);
    private static EntityDataAccessor<ItemStack> stackKey() {
        return SynchedEntityData.defineId(Collector.class, EntityDataSerializers.ITEM_STACK);
    }
    private static final List<EntityDataAccessor<ItemStack>> SIGNS = List.of(stackKey(), stackKey(), stackKey());
    private static final EntityDataAccessor<ItemStack> CARGO = stackKey();
    private static final TagKey<Item> REQUESTS = TagKey.create(Registries.ITEM, id("collector/requests"));
    private static final TagKey<Item> REWARDS = TagKey.create(Registries.ITEM, id("collector/rewards"));
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.collector.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.collector.walk");
    private static final RawAnimation EXCHANGE = RawAnimation.begin().thenPlay("animation.collector.collect").thenPlay("animation.collector.give");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ItemStack[] rewards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final long[] renewAt = new long[3];
    private ItemStack pending = ItemStack.EMPTY;
    private UUID recipient;
    private int actionTicks;

    public Collector(EntityType<? extends Collector> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .22);
    }
    public static boolean placement(EntityType<Collector> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return ServerConfigs.COLLECTOR.canSpawn() && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData group) {
        Holder<Biome> biome = level.getBiome(blockPosition());
        for (Coat coat : Coat.values()) if (biome.is(coat.habitats)) {
            entityData.set(COAT, coat.key);
            break;
        }
        return super.finalizeSpawn(level,difficulty,reason,group);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COAT, Coat.PRAIRIE.key);
        for (var key : SIGNS) builder.define(key, ItemStack.EMPTY);
        builder.define(CARGO, ItemStack.EMPTY);
    }
    public String coat() { return entityData.get(COAT); }
    public ItemStack sign(int i) { return entityData.get(SIGNS.get(i)); }
    public ItemStack cargo() { return entityData.get(CARGO); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BarterGoal());
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, .85));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 7));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }
    private ItemStack roll(TagKey<Item> pool) {
        return BuiltInRegistries.ITEM.getTag(pool).flatMap(set -> set.getRandomElement(random))
                .map(item -> new ItemStack(item.value())).orElse(ItemStack.EMPTY);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        long now = level().getGameTime();
        for (int i=0; i<3; i++) {
            if (!sign(i).isEmpty() || now < renewAt[i]) continue;
            ItemStack input = roll(REQUESTS);
            ItemStack output = roll(REWARDS);
            if (input.isEmpty() || output.isEmpty()) {
                renewAt[i] = now + 100;
                continue;
            }
            rewards[i] = output;
            entityData.set(SIGNS.get(i), input);
        }
        if (actionTicks > 0) {
            actionTicks--;
            if (actionTicks == 28) entityData.set(CARGO, ItemStack.EMPTY);
        }
        if (actionTicks <= 14 && !pending.isEmpty()) deliver();
    }
    private int matchingSlot(ItemEntity item) {
        if (!item.isAlive() || !(item.getOwner() instanceof Player)) return -1;
        for (int i=0; i<3; i++) if (!sign(i).isEmpty() && item.getItem().is(sign(i).getItem())) return i;
        return -1;
    }
    private final class BarterGoal extends Goal {
        private ItemEntity target;
        private long nextScan;
        BarterGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (actionTicks > 0 || !pending.isEmpty()) return true;
            long now = level().getGameTime();
            if (now < nextScan) return false;
            nextScan = now + 18 + random.nextInt(7);
            target = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8), item -> matchingSlot(item)>=0)
                    .stream().min(java.util.Comparator.comparingDouble(Collector.this::distanceToSqr)).orElse(null);
            return target != null;
        }
        @Override public boolean canContinueToUse() {
            return actionTicks > 0 || target != null && matchingSlot(target)>=0 && distanceToSqr(target)<100 && !getNavigation().isDone();
        }
        @Override public void start() { if (target != null) getNavigation().moveTo(target, 1.05); }
        @Override public void stop() { target = null; getNavigation().stop(); }
        @Override public void tick() {
            if (target == null || actionTicks > 0) return;
            getLookControl().setLookAt(target, 35, 25);
            if (distanceToSqr(target)>2.8 || !hasLineOfSight(target)) {
                if (tickCount % 10 == 0) getNavigation().moveTo(target, 1.05);
                return;
            }
            int i = matchingSlot(target);
            if (i < 0) return;
            lookAt(target, 180, 45);
            setYBodyRot(getYHeadRot());
            recipient = target.getOwner().getUUID();
            ItemStack stack = target.getItem().copy();
            entityData.set(CARGO, stack.copyWithCount(1));
            stack.shrink(1);
            if (stack.isEmpty()) target.discard(); else target.setItem(stack);
            pending = rewards[i];
            rewards[i] = ItemStack.EMPTY;
            entityData.set(SIGNS.get(i), ItemStack.EMPTY);
            renewAt[i] = level().getGameTime() + 6000;
            actionTicks = 68;
            getNavigation().stop();
            target = null;
            triggerAnim("activity","exchange");
        }
    }
    private void deliver() {
        var forward = net.minecraft.world.phys.Vec3.directionFromRotation(0, yBodyRot);
        var pos = position().add(forward.scale(.85)).add(0,.72,0);
        var drop = new ItemEntity(level(),pos.x,pos.y,pos.z,pending.copy());
        if (recipient != null) drop.setTarget(recipient);
        Player player = recipient == null ? null : level().getPlayerByUUID(recipient);
        var direction = player != null ? player.position().subtract(position()).multiply(1,0,1).normalize() : forward;
        drop.setDeltaMovement(direction.scale(.19).add(0,.13,0));
        if (level().addFreshEntity(drop)) pending = ItemStack.EMPTY;
    }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource damage, boolean recentlyHit) {
        super.dropCustomDeathLoot(level,damage,recentlyHit);
        if (!pending.isEmpty()) deliver();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Coat",coat());
        var offers = new ListTag();
        for (int i=0; i<3; i++) {
            var offer = new CompoundTag();
            offer.put("Request",sign(i).saveOptional(registryAccess()));
            offer.put("Reward",rewards[i].saveOptional(registryAccess()));
            offer.putLong("RenewAt",renewAt[i]);
            offers.add(offer);
        }
        tag.put("Offers",offers);
        tag.put("PendingReward",pending.saveOptional(registryAccess()));
        if (recipient!=null && !pending.isEmpty()) tag.putUUID("Recipient",recipient);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(COAT,Coat.parse(tag.getString("Coat")).key);
        var offers = tag.getList("Offers",Tag.TAG_COMPOUND);
        for (int i=0; i<Math.min(offers.size(),3); i++) {
            var offer = offers.getCompound(i);
            rewards[i]=ItemStack.parseOptional(registryAccess(),offer.getCompound("Reward"));
            entityData.set(SIGNS.get(i),rewards[i].isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registryAccess(),offer.getCompound("Request")));
            renewAt[i]=offer.getLong("RenewAt");
        }
        pending = ItemStack.parseOptional(registryAccess(),tag.getCompound("PendingReward"));
        recipient = tag.hasUUID("Recipient") ? tag.getUUID("Recipient") : null;
        actionTicks = pending.isEmpty() ? 0 : 2;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"activity",2,state -> state.setAndContinue(state.isMoving() ? WALK : IDLE))
                .triggerableAnim("exchange",EXCHANGE));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}


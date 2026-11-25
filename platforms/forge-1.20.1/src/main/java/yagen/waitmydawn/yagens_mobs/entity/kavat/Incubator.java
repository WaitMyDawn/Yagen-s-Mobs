package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import yagen.waitmydawn.yagens_mobs.registries.*;

public final class Incubator extends BlockEntity implements GeoBlockEntity {
    static final int SHUTTER_TICKS = 24;
    private static final RawAnimation TRAVEL = RawAnimation.begin().thenPlayAndHold("animation.incubator.close");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long shutterStart = -1;
    private float initialTravel, sampledTravel;

    @Override
    public void setBlockState(BlockState state) {
        if (level != null && state.getValue(IncubatorBlock.CLOSED) != getBlockState().getValue(IncubatorBlock.CLOSED)) {
            initialTravel = shutterTravel(0);
            shutterStart = level.getGameTime();
        }
        super.setBlockState(state);
    }

    float shutterTravel(float partial) {
        boolean closed = getBlockState().getValue(IncubatorBlock.CLOSED);
        if (shutterStart < 0 || level == null) return closed ? SHUTTER_TICKS : 0;
        float elapsed = (level.getGameTime() - shutterStart) + partial;
        return Mth.clamp(initialTravel + (closed ? elapsed : -elapsed), 0, SHUTTER_TICKS);
    }

    void sampleShutters(float partial) { sampledTravel = shutterTravel(partial); }

    // The cell directly above the controller is in the same chunk even at a chunk boundary.
    boolean isPacked() {
        return level == null || !IncubatorBlock.matches(level.getBlockState(worldPosition.above()), 4);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Sample the same authored motion backwards when opening, including reversals mid-stroke.
        controllers.add(new AnimationController<>(this, "shutters", 0, state -> state.setAndContinue(TRAVEL)) {
            @Override protected double adjustTick(double tick) {
                if (shouldResetTick) {
                    super.adjustTick(tick);
                    if (getAnimationState() == State.TRANSITIONING) return 0;
                }
                return Math.min(SHUTTER_TICKS - .001, sampledTravel);
            }
        });
    }
    final IncubatorSamples samples = new IncubatorSamples();
    private CompoundTag job = new CompoundTag();
    private boolean assembled;
    public Incubator(BlockPos pos, BlockState state) {
        super(ModBlocks.INCUBATOR_ENTITY.get(), pos, state);
        samples.addListener(container -> setChanged());
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).expandTowards(1, 1, 1);
    }

    boolean busy() {
        return job.contains("Cat", 10);
    }
    int seconds() {
        return (int) Math.max(0, (job.getLong("Due") - level.getGameTime() + 19) / 20);
    }
    void breed(ServerPlayer player) {
        if (busy() || !canStart(player)) return;
        boolean imprint = samples.hasImprint();
        List<KavatSample> parents = new ArrayList<>();
        List<ItemStack> escrow = new ArrayList<>();
        for (var stack : samples.snapshot()) {
            if (stack.isEmpty()) continue;
            var sample = KavatSample.read(stack);
            if (stack.getCount() != 1 || sample == null
                    || !stack.is(imprint ? ModItems.KAVAT_TEMPLATE.get() : ModItems.KAVAT_CODE.get())) return;
            parents.add(sample);
            escrow.add(stack.copy());
        }
        if (parents.size() != (imprint ? 2 : 10)) return;
        if (imprint && parents.stream().allMatch(s -> player.getUUID().equals(s.owner()))) {
            player.displayClientMessage(Component.translatable("kavat.own_templates"), true);
            return;
        }
        var rng = player.getRandom();
        var child = imprint
                ? KavatGenes.cross(new KavatGenes.Line(parents.get(0).genes(), parents.get(0).generation()),
                          new KavatGenes.Line(parents.get(1).genes(), parents.get(1).generation()), rng::nextInt)
                : new KavatGenes.Line(
                          KavatGenes.codes(parents.stream().map(KavatSample::genes).toList(), rng::nextInt), 0);
        var cat = ModEntities.KAVAT.get().create(level);
        if (cat == null)
            return;
        cat.phenotype(child.genes(), child.generation());
        cat.setOwnerUUID(player.getUUID());
        cat.setTame(true);
        cat.setPersistenceRequired();
        CompoundTag data = new CompoundTag();
        cat.saveWithoutId(data);
        samples.clearContent();
        begin(player, data, escrow, false);
    }
    void revive(ServerPlayer p, String id) {
        CompoundTag memories = KavatMemories.memories(p);
        if (busy() || !memories.contains(id, 10) || !canStart(p))
            return;
        CompoundTag data = memories.getCompound(id);
        var material = KavatSample.MATERIALS.get(KavatGenes.unpack(data.getInt("KavatTraits")).coat());
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            if (p.getInventory().getItem(i).is(material)) {
                begin(p, data.copy(), List.of(p.getInventory().removeItem(i, 1)), true);
                memories.remove(id);
                return;
            }
        }
        p.displayClientMessage(Component.translatable("kavat.cost", material.getDescription()), true);
    }
    private void begin(ServerPlayer p, CompoundTag cat, List<ItemStack> paid, boolean revival) {
        job = new CompoundTag();
        job.putLong("Due", level.getGameTime() + 6000);
        job.putUUID("Owner", p.getUUID());
        job.put("Cat", cat);
        job.putBoolean("Revival", revival);
        ListTag list = new ListTag();
        paid.forEach(s -> list.add(s.save(new CompoundTag())));
        job.put("Paid", list);
        setChanged();
        syncCover();
    }
    private boolean canStart(ServerPlayer player) {
        if (!ensureChamber()) {
            player.displayClientMessage(Component.translatable("kavat.chamber_space"), true);
            return false;
        }
        if (IncubatorBlock.occupied(level, worldPosition)) {
            player.displayClientMessage(Component.translatable("kavat.chamber_occupied"), true);
            return false;
        }
        return true;
    }

    boolean ensureChamber() {
        if (!IncubatorBlock.available(level, worldPosition)) return false;
        if (assembled) return true;
        boolean missing = false;
        for (int i = 1; i < 8; i++) {
            var state = level.getBlockState(IncubatorBlock.cell(worldPosition, i));
            if (IncubatorBlock.matches(state, i)) continue;
            // A legacy machine may be embedded in a build. Expansion only claims empty cells.
            if (!state.isAir()) return false;
            missing = true;
        }
        if (missing) {
            if (IncubatorBlock.occupied(level, worldPosition)) return false;
            IncubatorBlock.install(level, worldPosition, busy());
        }
        assembled = true;
        syncCover();
        return true;
    }

    private void syncCover() {
        boolean closed = busy();
        for (int i = 0; i < 8; i++) {
            BlockPos pos = IncubatorBlock.cell(worldPosition, i);
            var state = level.getBlockState(pos);
            if (IncubatorBlock.matches(state, i) && state.getValue(IncubatorBlock.CLOSED) != closed)
                level.setBlock(pos, state.setValue(IncubatorBlock.CLOSED, closed), Block.UPDATE_CLIENTS);
        }
    }

    void interrupt(ServerLevel server) {
        net.minecraft.world.Containers.dropContents(server, worldPosition, samples);
        if (!busy())
            return;
        CompoundTag cat = job.getCompound("Cat");
        if (job.getBoolean("Revival"))
            KavatMemories.store(server, job.getUUID("Owner"), cat.getUUID("UUID"), cat);
        for (var entry : job.getList("Paid", 10))
            Block.popResource(server, worldPosition, ItemStack.of((CompoundTag)entry));
        job = new CompoundTag();
        setChanged();
    }
    static void tick(Level level, BlockPos pos, BlockState state, Incubator inc) {
        long now = level.getGameTime();
        if (!inc.assembled && (now % 20 != 0 || !inc.ensureChamber()) || !inc.busy()) return;
        long next = inc.job.getLong("Due");
        if (now < next || now != next && now % 20 != 0 || !IncubatorBlock.available(level, pos))
            return;
        ServerLevel server = (ServerLevel) level;
        CompoundTag snapshot = inc.job.getCompound("Cat");
        Kavat cat = ModEntities.KAVAT.get().create(server);
        if (cat == null)
            return;
        CompoundTag data = snapshot.copy();
        for (String key : List.of("DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "FallDistance", "Motion",
                     "Passengers", "leash", "PortalCooldown"))
            data.remove(key);
        cat.load(data);
        cat.refreshDimensions();
        cat.setHealth(cat.getMaxHealth());
        cat.setAirSupply(cat.getMaxAirSupply());
        cat.clearFire();
        cat.setPortalCooldown();
        cat.moveTo(pos.getX() + 1, pos.getY() + IncubatorBlock.FLOOR, pos.getZ() + 1, 0, 0);
        var box = cat.getBoundingBox();
        if (IncubatorBlock.occupied(server, pos) || server.containsAnyLiquid(box) || !server.noCollision(cat)) return;
        cat.beginChamberExit(now);
        if (server.addFreshEntity(cat)) {
            inc.job = new CompoundTag();
            inc.setChanged();
            inc.syncCover();
        }
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        writeShutterClock(tag);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        handleUpdateTag(packet.getTag());
    }
    @Override public void handleUpdateTag(CompoundTag tag) { readShutterClock(tag); }

    private void writeShutterClock(CompoundTag tag) {
        tag.putLong("ShutterStart", shutterStart);
        tag.putFloat("InitialTravel", initialTravel);
    }
    private void readShutterClock(CompoundTag tag) {
        shutterStart = tag.contains("ShutterStart", Tag.TAG_LONG) ? tag.getLong("ShutterStart") : -1;
        initialTravel = tag.getFloat("InitialTravel");
    }
    @Override
    protected void saveAdditional(CompoundTag n) {
        super.saveAdditional(n);
        writeShutterClock(n);
        n.put("Job", job);
        net.minecraft.world.ContainerHelper.saveAllItems(n, samples.snapshot());
    }
    @Override
    public void load(CompoundTag n) {
        super.load(n);
        readShutterClock(n);
        job = n.getCompound("Job");
        samples.loadSamples(n);
    }
}

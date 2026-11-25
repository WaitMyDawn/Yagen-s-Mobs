package yagen.waitmydawn.yagens_mobs.entity.kavat;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
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
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
import com.geckolib.util.GeckoLibUtil;
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
        controllers.add(new AnimationController<Incubator>("shutters", 0, state -> {
            var result = state.setAndContinue(TRAVEL);
            if (state.controller().getTimeline() != null) state.controller().setAnimationTime(Math.min(SHUTTER_TICKS - .001, sampledTravel) / 20.0);
            return result;
        }));
    }
    final IncubatorSamples samples = new IncubatorSamples(this::setChanged);
    private CompoundTag job = new CompoundTag();
    private boolean assembled;
    public Incubator(BlockPos pos, BlockState state) {
        super(ModBlocks.INCUBATOR_ENTITY.get(), pos, state);
    }
    boolean busy() {
        return job.contains("Cat");
    }
    int seconds() {
        return (int) Math.max(0, (job.getLongOr("Due", 0L) - level.getGameTime() + 19) / 20);
    }
    void breed(ServerPlayer player) {
        if (busy() || !canStart(player)) return;
        boolean imprint = samples.hasImprint();
        List<KavatSample> parents = new ArrayList<>();
        List<ItemStack> escrow = new ArrayList<>();
        for (var stack : samples.getItems()) {
            if (stack.isEmpty()) continue;
            var sample = KavatSample.read(stack);
            if (stack.getCount() != 1 || sample == null
                    || !stack.is(imprint ? ModItems.KAVAT_TEMPLATE.get() : ModItems.KAVAT_CODE.get())) return;
            parents.add(sample);
            escrow.add(stack.copy());
        }
        if (parents.size() != (imprint ? 2 : 10)) return;
        if (imprint && parents.stream().allMatch(s -> player.getUUID().equals(s.owner()))) {
            player.sendOverlayMessage(Component.translatable("kavat.own_templates"));
            return;
        }
        var rng = player.getRandom();
        var child = imprint
                ? KavatGenes.cross(new KavatGenes.Line(parents.get(0).genes(), parents.get(0).generation()),
                          new KavatGenes.Line(parents.get(1).genes(), parents.get(1).generation()), rng::nextInt)
                : new KavatGenes.Line(
                          KavatGenes.codes(parents.stream().map(KavatSample::genes).toList(), rng::nextInt), 0);
        var cat = ModEntities.KAVAT.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (cat == null)
            return;
        cat.phenotype(child.genes(), child.generation());
        cat.setOwnerReference(net.minecraft.world.entity.EntityReference.of(player));
        cat.setTame(true, false);
        cat.setPersistenceRequired();
        var output = SaveTag.output(level.registryAccess());
        cat.saveWithoutId(output);
        CompoundTag data = output.buildResult();
        samples.clearContent();
        begin(player, data, escrow, false);
    }
    void revive(ServerPlayer p, String id) {
        CompoundTag memories = KavatMemories.memories(p);
        if (busy() || !memories.contains(id) || !canStart(p))
            return;
        CompoundTag data = memories.getCompoundOrEmpty(id);
        var material = KavatSample.MATERIALS.get(KavatGenes.unpack(data.getIntOr("KavatTraits", 0)).coat());
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(material)) {
                begin(p, data.copy(), List.of(p.getInventory().removeItem(i, 1)), true);
                KavatMemories.remove(p, id);
                return;
            }
        }
        p.sendOverlayMessage(Component.translatable("kavat.cost", material.getDefaultInstance().getHoverName()));
    }
    private void begin(ServerPlayer p, CompoundTag cat, List<ItemStack> paid, boolean revival) {
        job = new CompoundTag();
        job.putLong("Due", level.getGameTime() + 6000);
        SaveTag.putId(job, "Owner", p.getUUID());
        job.put("Cat", cat);
        job.putBoolean("Revival", revival);
        ListTag list = new ListTag();
        paid.forEach(s -> list.add(SaveTag.saveItem(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),s)));
        job.put("Paid", list);
        setChanged();
        syncCover();
    }
    private boolean canStart(ServerPlayer player) {
        if (!ensureChamber()) {
            player.sendOverlayMessage(Component.translatable("kavat.chamber_space"));
            return false;
        }
        if (IncubatorBlock.occupied(level, worldPosition)) {
            player.sendOverlayMessage(Component.translatable("kavat.chamber_occupied"));
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) interrupt(server);
        super.preRemoveSideEffects(pos, state);
    }

    private void interrupt(ServerLevel server) {
        net.minecraft.world.Containers.dropContents(server, worldPosition, samples);
        if (!busy())
            return;
        CompoundTag cat = job.getCompoundOrEmpty("Cat");
        if (job.getBooleanOr("Revival", false))
            KavatMemories.store(server, SaveTag.id(job,"Owner").orElseThrow(), SaveTag.id(cat,"UUID").orElseThrow(), cat);
        for (var entry : job.getListOrEmpty("Paid"))
            ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),entry).result().ifPresent(s -> Block.popResource(server, worldPosition, s));
        job = new CompoundTag();
        setChanged();
    }
    static void tick(Level level, BlockPos pos, BlockState state, Incubator inc) {
        long now = level.getGameTime();
        if (!inc.assembled && (now % 20 != 0 || !inc.ensureChamber()) || !inc.busy()) return;
        long next = inc.job.getLongOr("Due", 0L);
        if (now < next || now != next && now % 20 != 0 || !IncubatorBlock.available(level, pos))
            return;
        ServerLevel server = (ServerLevel) level;
        CompoundTag snapshot = inc.job.getCompoundOrEmpty("Cat");
        Kavat cat = ModEntities.KAVAT.get().create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (cat == null)
            return;
        CompoundTag data = snapshot.copy();
        for (String key : List.of("DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "fall_distance", "Motion",
                     "Passengers", "leash", "PortalCooldown"))
            data.remove(key);
        cat.load(SaveTag.input(server.registryAccess(),data));
        cat.refreshDimensions();
        cat.setHealth(cat.getMaxHealth());
        cat.setAirSupply(cat.getMaxAirSupply());
        cat.clearFire();
        cat.setPortalCooldown();
        cat.snapTo(pos.getX() + 1, pos.getY() + IncubatorBlock.FLOOR, pos.getZ() + 1, 0, 0);
        var box = cat.getBoundingBox();
        if (IncubatorBlock.occupied(server, pos) || server.containsAnyLiquid(box) || !server.noCollision(cat)) return;
        cat.beginChamberExit(now);
        if (server.addFreshEntity(cat)) {
            inc.job = new CompoundTag();
            inc.setChanged();
            inc.syncCover();
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        writeShutterClock(tag);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    private void writeShutterClock(CompoundTag tag) {
        tag.putLong("ShutterStart", shutterStart);
        tag.putFloat("InitialTravel", initialTravel);
    }
    private void readShutterClock(CompoundTag tag) {
        shutterStart = tag.contains("ShutterStart") ? tag.getLongOr("ShutterStart", 0L) : -1;
        initialTravel = tag.getFloatOr("InitialTravel", 0f);
    }
    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        var n = new CompoundTag();
        writeShutterClock(n);
        n.put("Job", job);
        SaveTag.write(output,n);
        net.minecraft.world.ContainerHelper.saveAllItems(output,samples.getItems());
    }
    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        var n = SaveTag.read(input);
        readShutterClock(n);
        job = n.getCompoundOrEmpty("Job");
        samples.getItems().clear();
        net.minecraft.world.ContainerHelper.loadAllItems(input,samples.getItems());
    }
}

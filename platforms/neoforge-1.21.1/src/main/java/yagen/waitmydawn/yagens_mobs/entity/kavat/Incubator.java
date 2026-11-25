package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import yagen.waitmydawn.yagens_mobs.registries.*;

public final class Incubator extends BlockEntity {
    private CompoundTag job = new CompoundTag();
    public Incubator(BlockPos pos, BlockState state) {
        super(ModBlocks.INCUBATOR_ENTITY.get(), pos, state);
    }
    boolean busy() {
        return job.contains("Cat", 10);
    }
    int seconds() {
        return (int) Math.max(0, (job.getLong("Due") - level.getGameTime() + 19) / 20);
    }
    List<Kavat> pets(ServerPlayer owner) {
        return level
                .getEntitiesOfClass(Kavat.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(8),
                        k
                        -> k.isAlive() && !k.decoy() && k.isOwnedBy(owner)
                                && k.distanceToSqr(worldPosition.getCenter()) <= 64)
                .stream()
                .sorted(Comparator.comparing(Kavat::getUUID))
                .toList();
    }
    void breed(ServerPlayer player, List<Integer> slots) {
        if (busy() || slots.size() != 2 && slots.size() != 10)
            return;
        boolean imprint = slots.size() == 2;
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        List<KavatSample> samples = new ArrayList<>();
        for (int slot : slots) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.is(imprint ? ModItems.KAVAT_TEMPLATE.get() : ModItems.KAVAT_CODE.get())
                    || counts.merge(slot, 1, Integer::sum) > stack.getCount())
                return;
            var s = KavatSample.read(stack);
            if (s == null)
                return;
            samples.add(s);
        }
        if (imprint && samples.stream().allMatch(s -> player.getUUID().equals(s.owner()))) {
            player.displayClientMessage(Component.translatable("kavat.own_templates"), true);
            return;
        }
        var rng = player.getRandom();
        var child = imprint
                ? KavatGenes.cross(new KavatGenes.Line(samples.get(0).genes(), samples.get(0).generation()),
                          new KavatGenes.Line(samples.get(1).genes(), samples.get(1).generation()), rng::nextInt)
                : new KavatGenes.Line(
                          KavatGenes.codes(samples.stream().map(KavatSample::genes).toList(), rng::nextInt), 0);
        var cat = ModEntities.KAVAT.get().create(level);
        if (cat == null)
            return;
        cat.phenotype(child.genes(), child.generation());
        cat.setOwnerUUID(player.getUUID());
        cat.setTame(true, false);
        cat.setPersistenceRequired();
        CompoundTag data = new CompoundTag();
        cat.saveWithoutId(data);
        List<ItemStack> escrow = new ArrayList<>();
        counts.forEach((slot, n) -> escrow.add(player.getInventory().removeItem(slot, n)));
        begin(player, data, escrow, false);
    }
    void revive(ServerPlayer p, String id) {
        CompoundTag memories = KavatMemories.memories(p);
        if (busy() || !memories.contains(id, 10))
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
        paid.forEach(s -> list.add(s.save(level.registryAccess())));
        job.put("Paid", list);
        setChanged();
    }
    void interrupt(ServerLevel server) {
        if (!busy())
            return;
        CompoundTag cat = job.getCompound("Cat");
        if (job.getBoolean("Revival"))
            KavatMemories.store(server, job.getUUID("Owner"), cat.getUUID("UUID"), cat);
        for (var entry : job.getList("Paid", 10))
            ItemStack.parse(server.registryAccess(), entry).ifPresent(s -> Block.popResource(server, worldPosition, s));
        job = new CompoundTag();
        setChanged();
    }
    static void tick(Level level, BlockPos pos, BlockState state, Incubator inc) {
        if (!inc.busy() || level.getGameTime() % 20 != 0 || inc.seconds() > 0)
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
        cat.setHealth(cat.getMaxHealth());
        cat.setAirSupply(cat.getMaxAirSupply());
        cat.clearFire();
        cat.setPortalCooldown();
        for (BlockPos place : List.of(pos.above(), pos.north(), pos.south(), pos.east(), pos.west())) {
            cat.moveTo(place.getX() + .5, place.getY(), place.getZ() + .5, 0, 0);
            if (server.noCollision(cat) && server.addFreshEntity(cat)) {
                inc.job = new CompoundTag();
                inc.setChanged();
                return;
            }
        }
    }
    @Override
    protected void saveAdditional(CompoundTag n, HolderLookup.Provider lookup) {
        super.saveAdditional(n, lookup);
        n.put("Job", job);
    }
    @Override
    protected void loadAdditional(CompoundTag n, HolderLookup.Provider lookup) {
        super.loadAdditional(n, lookup);
        job = n.getCompound("Job");
    }
}

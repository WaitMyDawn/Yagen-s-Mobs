package yagen.waitmydawn.yagens_mobs.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.*;
import net.minecraft.world.level.pathfinder.*;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.TickEvent.*;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterfly;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenSwarm;
import yagen.waitmydawn.yagens_mobs.entity.kavat.Kavat;
import yagen.waitmydawn.yagens_mobs.mixin.CompanionTeleportSequence;

/** Server-wide index of real followers. A chunk unload retains identity, a destructive removal releases it. */
public final class CompanionTravel extends SavedData {
    private static final TicketType<UUID> RECALL = TicketType.create("yagens_companion_recall", UUID::compareTo, 100);
    private final Map<UUID, Map<UUID, Entry>> owners = new HashMap<>();
    private final Map<ServerPlayer, Integer> teleports = new WeakHashMap<>();
    private static final class Entry {
        final boolean aquatic;
        ResourceKey<Level> dimension;
        long chunk;
        boolean requested;
        long nextTicket;
        Entry(boolean aquatic, ResourceKey<Level> dimension, long chunk) {
            this.aquatic = aquatic;
            this.dimension = dimension;
            this.chunk = chunk;
        }
    }

    private static CompanionTravel get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(CompanionTravel::load, CompanionTravel::new, "yagens_mobs_companion_travel");
    }

    private static boolean supported(Entity entity) {
        return entity instanceof Kavat cat && !cat.decoy() || entity instanceof BubbleSalamander;
    }

    private static boolean follows(TamableAnimal pet) {
        return pet.isAlive() && pet.isTame() && !pet.isOrderedToSit() && !pet.isPassenger() && !pet.isLeashed()
                && (!(pet instanceof BubbleSalamander swimmer) || !swimmer.isWorn());
    }

    private void remember(TamableAnimal pet) {
        UUID owner = pet.getOwnerUUID();
        if (owner == null) return;
        if (!follows(pet)) {
            forget(owner, pet.getUUID());
            return;
        }
        var pets = owners.computeIfAbsent(owner, key -> new HashMap<>());
        var entry = pets.get(pet.getUUID());
        long chunk = pet.chunkPosition().toLong();
        if (entry == null) {
            pets.put(pet.getUUID(), new Entry(pet instanceof BubbleSalamander, pet.level().dimension(), chunk));
        } else if (entry.chunk != chunk || entry.dimension != pet.level().dimension()) {
            entry.chunk = chunk;
            entry.dimension = pet.level().dimension();
        } else {
            return;
        }
        setDirty();
    }

    private void forget(UUID owner, UUID pet) {
        var pets = owners.get(owner);
        if (pets != null && pets.remove(pet) != null) {
            if (pets.isEmpty()) owners.remove(owner);
            setDirty();
        }
    }

    public static void moved(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (supported(event.getEntity()) && event.getEntity() instanceof TamableAnimal pet && pet.isTame()
                && pet.level() instanceof ServerLevel level) get(level).remember(pet);
    }

    public static void sectionChanged(EntityEvent.EnteringSection event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof LumenButterfly butterfly)
            LumenSwarm.rememberLocation(level, butterfly);
        else if (supported(event.getEntity()) && event.getEntity() instanceof TamableAnimal pet && pet.isTame())
            get(level).remember(pet);
    }

    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && supported(event.getEntity()))
            get(level).remember((TamableAnimal) event.getEntity());
    }

    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && supported(event.getEntity())) {
            var pet = (TamableAnimal) event.getEntity();
            var reason = pet.getRemovalReason();
            // A dimension transition registers the replacement in its destination.
            if (reason != null && reason.shouldDestroy() && pet.getOwnerUUID() != null)
                get(level).forget(pet.getOwnerUUID(), pet.getUUID());
            else if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK && pet.getOwnerUUID() != null) {
                var data = get(level);
                var pets = data.owners.get(pet.getOwnerUUID());
                var entry = pets == null ? null : pets.get(pet.getUUID());
                if (entry != null && entry.chunk != pet.chunkPosition().toLong()) {
                    entry.chunk = pet.chunkPosition().toLong();
                    data.setDirty();
                }
            }
        }
    }

    public static void playerTick(PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()
                || owner.connection == null) return;
        var data = get(owner.serverLevel());
        int sequence = ((CompanionTeleportSequence) owner.connection).yagens$teleportSequence();
        Integer last = data.teleports.put(owner, sequence);
        boolean teleported = last == null || last != sequence;
        var pets = data.owners.get(owner.getUUID());
        if (pets == null || !teleported && owner.tickCount % 5 != 0) return;
        for (var pair : List.copyOf(pets.entrySet())) {
            var entry = pair.getValue();
            if (teleported) entry.requested = true;
            data.recall(owner, pair.getKey(), entry);
        }
    }

    private void recall(ServerPlayer owner, UUID id, Entry entry) {
        var destination = owner.serverLevel();
        if (entry.aquatic && (!entry.requested || !destination.getFluidState(owner.blockPosition()).is(FluidTags.WATER))) {
            entry.requested = false;
            return;
        }
        var source = destination.getServer().getLevel(entry.dimension);
        if (source == null) return;
        if (!(source.getEntity(id) instanceof TamableAnimal pet)) {
            long time = destination.getGameTime();
            if (entry.nextTicket != 0 && time >= entry.nextTicket
                    && source.areEntitiesLoaded(entry.chunk)
                    && source.getChunkSource().isPositionTicking(entry.chunk)) {
                forget(owner.getUUID(), id);
                return;
            }
            if (time >= entry.nextTicket) {
                source.getChunkSource().addRegionTicket(RECALL, new ChunkPos(entry.chunk), 2, id);
                entry.nextTicket = time + 60;
            }
            return;
        }
        if (!owner.getUUID().equals(pet.getOwnerUUID()) || !follows(pet)) {
            forget(owner.getUUID(), id);
            return;
        }
        double range = entry.requested ? 3 : 64;
        if (source == destination && pet.distanceToSqr(owner) <= range * range) {
            entry.requested = false;
            return;
        }
        Vec3 target = landing(destination, owner, pet, entry.aquatic);
        if (target == null || !destination.isPositionEntityTicking(BlockPos.containing(target))) return;
        pet.getNavigation().stop();
        pet.setTarget(null);
        if (source != destination) {
            if (!(pet.changeDimension(destination, new ITeleporter() {
                @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel world, java.util.function.Function<ServerLevel,PortalInfo> vanilla) { return new PortalInfo(target,Vec3.ZERO,owner.getYRot(),0); }
                @Override public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw,
                        java.util.function.Function<Boolean,Entity> reposition) { return reposition.apply(false); }
            }) instanceof TamableAnimal arrived)) return;
            pet = arrived;
        } else {
            pet.teleportTo(target.x, target.y, target.z);
        }
        pet.setDeltaMovement(Vec3.ZERO);
        pet.fallDistance = 0;
        entry.requested = false;
        remember(pet);
    }

    private static Vec3 landing(ServerLevel level, ServerPlayer owner, TamableAnimal pet, boolean aquatic) {
        
        for (var candidate : BlockPos.withinManhattan(owner.blockPosition(), 4, 3, 4)) {
            if (candidate.getX() == owner.getBlockX() && candidate.getZ() == owner.getBlockZ()) continue;
            if (!level.hasChunkAt(candidate) || !level.getWorldBorder().isWithinBounds(candidate)
                    || level.isOutsideBuildHeight(candidate)) continue;
            Vec3 point = Vec3.atBottomCenterOf(candidate);
            var box = pet.getDimensions(pet.getPose()).makeBoundingBox(point);
            var fluid = level.getFluidState(candidate);
            if (fluid.is(FluidTags.WATER)) {
                if (!aquatic && !owner.isInWater()) continue;
            } else if (aquatic || !fluid.isEmpty()
                    || WalkNodeEvaluator.getBlockPathTypeStatic(level, candidate.mutable()) != BlockPathTypes.WALKABLE) {
                continue;
            }
            if (PetHazards.safe(level, box, false) && !box.intersects(owner.getBoundingBox()) && level.noCollision(pet.level() == level ? pet : null, box))
                return point;
        }
        return null;
    }

    private static CompanionTravel load(CompoundTag tag) {
        var data = new CompanionTravel();
        for (Tag item : tag.getList("Followers", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
            if (dimension != null && row.hasUUID("Owner") && row.hasUUID("Pet")) {
                data.owners.computeIfAbsent(row.getUUID("Owner"), key -> new HashMap<>()).put(row.getUUID("Pet"),
                        new Entry(row.getBoolean("Aquatic"), ResourceKey.create(Registries.DIMENSION, dimension), row.getLong("Chunk")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag rows = new ListTag();
        owners.forEach((owner, pets) -> pets.forEach((id, entry) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Owner", owner);
            row.putUUID("Pet", id);
            row.putString("Dimension", entry.dimension.location().toString());
            row.putLong("Chunk", entry.chunk);
            row.putBoolean("Aquatic", entry.aquatic);
            rows.add(row);
        }));
        tag.put("Followers", rows);
        return tag;
    }
}

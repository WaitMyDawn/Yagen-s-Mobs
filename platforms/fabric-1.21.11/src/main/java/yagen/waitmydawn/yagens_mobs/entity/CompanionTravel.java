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
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamander;
import yagen.waitmydawn.yagens_mobs.entity.kavat.Kavat;
import yagen.waitmydawn.yagens_mobs.mixin.CompanionTeleportSequence;

/** Server-wide index of real followers. A chunk unload retains identity, a destructive removal releases it. */
public final class CompanionTravel extends SavedData {
    private static final net.minecraft.world.level.saveddata.SavedDataType<CompanionTravel> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>("yagens_mobs_companion_travel",
                    CompanionTravel::new, CompoundTag.CODEC.xmap(CompanionTravel::load, data -> data.save(new CompoundTag())), null);
    private static final TicketType RECALL = Registry.register(net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("yagens_mobs","companion_recall"),
            new TicketType(100, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
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
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static boolean supported(Entity entity) {
        return entity instanceof Kavat cat && !cat.decoy() || entity instanceof BubbleSalamander;
    }

    private static boolean follows(TamableAnimal pet) {
        return pet.isAlive() && pet.isTame() && !pet.isOrderedToSit() && !pet.isPassenger() && !pet.isLeashed()
                && (!(pet instanceof BubbleSalamander swimmer) || !swimmer.isWorn());
    }

    private static UUID ownerOf(TamableAnimal pet) {
        var ref = pet.getOwnerReference();
        return ref == null ? null : ref.getUUID();
    }

    private void remember(TamableAnimal pet) {
        UUID owner = ownerOf(pet);
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

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(CompanionTravel::joined);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register(CompanionTravel::left);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(level -> level.players().forEach(CompanionTravel::playerTick));
    }

    public static void moved(TamableAnimal pet) {
        if (supported(pet) && pet.isTame() && !pet.level().isClientSide() && pet.level() instanceof ServerLevel level)
            get(level).remember(pet);
    }
    public static void joined(Entity entity, ServerLevel level) {
        if (supported(entity)) get(level).remember((TamableAnimal) entity);
    }
    public static void left(Entity entity, ServerLevel level) {
        if (supported(entity)) {
            var pet = (TamableAnimal) entity;
            var reason = pet.getRemovalReason();
            // A dimension transition registers the replacement in its destination.
            if (reason != null && reason.shouldDestroy() && ownerOf(pet) != null)
                get(level).forget(ownerOf(pet), pet.getUUID());
            else if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK && ownerOf(pet) != null) {
                var data = get(level);
                var pets = data.owners.get(ownerOf(pet));
                var entry = pets == null ? null : pets.get(pet.getUUID());
                if (entry != null && entry.chunk != pet.chunkPosition().toLong()) {
                    entry.chunk = pet.chunkPosition().toLong();
                    data.setDirty();
                }
            }
        }
    }

    public static void playerTick(ServerPlayer owner) {
        if (!owner.isAlive() || owner.isSpectator() || owner.connection == null) return;
        var data = get(owner.level());
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
        var destination = owner.level();
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
                    && source.isPositionEntityTicking(new ChunkPos(entry.chunk).getWorldPosition())) {
                forget(owner.getUUID(), id);
                return;
            }
            if (time >= entry.nextTicket) {
                source.getChunkSource().addTicketWithRadius(RECALL, new ChunkPos(entry.chunk), 2);
                entry.nextTicket = time + 60;
            }
            return;
        }
        if (!owner.getUUID().equals(ownerOf(pet)) || !follows(pet)) {
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
            if (!(pet.teleport(new TeleportTransition(destination, target, Vec3.ZERO, owner.getYRot(),
                    0, TeleportTransition.DO_NOTHING)) instanceof TamableAnimal arrived)) return;
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
        var context = new PathfindingContext(level, pet);
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
                    || WalkNodeEvaluator.getPathTypeStatic(context, candidate.mutable()) != PathType.WALKABLE) {
                continue;
            }
            if (PetHazards.safe(level, box, false) && !box.intersects(owner.getBoundingBox()) && level.noCollision(pet.level() == level ? pet : null, box))
                return point;
        }
        return null;
    }

    private static CompanionTravel load(CompoundTag tag) {
        var data = new CompanionTravel();
        for (Tag item : tag.getListOrEmpty("Followers")) {
            CompoundTag row = (CompoundTag) item;
            var dimension = Identifier.tryParse(row.getStringOr("Dimension",""));
            if (dimension != null && row.read("Owner", UUIDUtil.CODEC).isPresent() && row.read("Pet", UUIDUtil.CODEC).isPresent()) {
                data.owners.computeIfAbsent(row.read("Owner", UUIDUtil.CODEC).orElseThrow(), key -> new HashMap<>()).put(row.read("Pet", UUIDUtil.CODEC).orElseThrow(),
                        new Entry(row.getBooleanOr("Aquatic",false), ResourceKey.create(Registries.DIMENSION, dimension), row.getLongOr("Chunk",0)));
            }
        }
        return data;
    }

    private CompoundTag save(CompoundTag tag) {
        ListTag rows = new ListTag();
        owners.forEach((owner, pets) -> pets.forEach((id, entry) -> {
            CompoundTag row = new CompoundTag();
            row.store("Owner", UUIDUtil.CODEC, owner);
            row.store("Pet", UUIDUtil.CODEC, id);
            row.putString("Dimension", entry.dimension.identifier().toString());
            row.putLong("Chunk", entry.chunk);
            row.putBoolean("Aquatic", entry.aquatic);
            rows.add(row);
        }));
        tag.put("Followers", rows);
        return tag;
    }
}

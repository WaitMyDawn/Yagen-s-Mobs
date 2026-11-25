package yagen.waitmydawn.yagens_mobs.entity.command;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.yagens_mobs.entity.kavat.Kavat;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

public final class PetOrders {
    public static final int BATCH = 128;
    private record Truce(Set<UUID> targets, UUID owner, int attack, int defense, int retaliation) {}
    private static final Map<Mob, Truce> TRUCES = new WeakHashMap<>();

    private PetOrders() {}

    public static boolean belongsTo(Mob pet, Player owner) {
        if (pet instanceof Kavat kavat && kavat.decoy()) return false;
        return pet instanceof Velocipod pod ? pod.isOwnedBy(owner)
                : pet instanceof OwnableEntity ownable && ownable.getOwner() == owner;
    }

    public static void recall(ServerPlayer owner, int[] ids) {
        if (!owner.isAlive() || owner.isSpectator() || ids.length > BATCH) return;
        var level = owner.serverLevel();
        int range = 16 * (level.getServer().getPlayerList().getViewDistance() + 1);
        for (int id : ids) {
            if (!(level.getEntity(id) instanceof Mob pet) || !pet.isAlive() || !belongsTo(pet, owner)
                    || pet.distanceToSqr(owner) > (double) range * range || !owner.hasLineOfSight(pet)) continue;
            Set<UUID> targets = new HashSet<>();
            var target = pet.getTarget();
            if (target != null) targets.add(target.getUUID());
            var brain = pet.getBrain();
            var remembered = brain.getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (remembered != null) remembered.ifPresent(enemy -> targets.add(enemy.getUUID()));
            if (pet.getLastHurtByMob() != null) targets.add(pet.getLastHurtByMob().getUUID());
            if (owner.getLastHurtMob() != null) targets.add(owner.getLastHurtMob().getUUID());
            if (owner.getLastHurtByMob() != null) targets.add(owner.getLastHurtByMob().getUUID());
            if (targets.isEmpty()) continue;
            Truce previous = TRUCES.get(pet);
            if (previous != null && previous.owner().equals(owner.getUUID())
                    && previous.attack() == owner.getLastHurtMobTimestamp()
                    && previous.defense() == owner.getLastHurtByMobTimestamp()
                    && previous.retaliation() == pet.getLastHurtByMobTimestamp()) targets.addAll(previous.targets());
            if (pet instanceof NeutralMob neutral) neutral.stopBeingAngry();
            pet.setTarget(null);
            pet.setLastHurtByMob(null);
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            pet.getNavigation().stop();
            TRUCES.put(pet, new Truce(Set.copyOf(targets), owner.getUUID(), owner.getLastHurtMobTimestamp(),
                    owner.getLastHurtByMobTimestamp(), pet.getLastHurtByMobTimestamp()));
        }
    }

    public static boolean withheld(Mob pet, LivingEntity next) {
        if (next == null || pet.level().isClientSide) return false;
        Truce truce = TRUCES.get(pet);
        if (truce == null || !truce.targets().contains(next.getUUID())) return false;
        Player owner = pet.level().getPlayerByUUID(truce.owner());
        if (owner != null && belongsTo(pet, owner) && owner.getLastHurtMobTimestamp() == truce.attack()
                && owner.getLastHurtByMobTimestamp() == truce.defense()
                && pet.getLastHurtByMobTimestamp() == truce.retaliation()) return true;
        TRUCES.remove(pet);
        return false;
    }
}

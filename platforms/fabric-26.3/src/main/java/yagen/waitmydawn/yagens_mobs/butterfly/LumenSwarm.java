package yagen.waitmydawn.yagens_mobs.butterfly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class LumenSwarm {
    private static final double RANGE_SQUARED = 32 * 32;
    private static final int HALF_MINUTE = 600;

    private LumenSwarm() {
    }

    static List<LumenButterfly> followers(ServerPlayer owner) {
        ServerLevel level = owner.level();
        List<LumenButterfly> result = new ArrayList<>();
        for (UUID id : LumenLedger.get(level).followers(owner.getUUID())) {
            if (level.getEntity(id) instanceof LumenButterfly butterfly && butterfly.isAlive() && !butterfly.isBaby()
                    && !butterfly.isOrderedToSit() && owner.getUUID().equals(butterfly.ownerId())
                    && butterfly.distanceToSqr(owner) <= RANGE_SQUARED) {
                result.add(butterfly);
            }
        }
        return result;
    }

    static boolean hasBlue(ServerPlayer owner) {
        return followers(owner).stream().anyMatch(pet -> pet.hasPower(LumenColor.BLUE));
    }

    public static int routeExperience(ServerLevel level, LivingEntity defeated, Entity attacker, int xp) {
        if (xp <= 0 || !(attacker instanceof ServerPlayer owner) || owner.level() != level) {
            return xp;
        }
        List<LumenButterfly> swarm = followers(owner);
        if (swarm.isEmpty()) {
            return xp;
        }
        int green = count(swarm, LumenColor.GREEN);
        double share = xp * (1.0 + green) / swarm.size();
        Vec3 origin = defeated.position().add(0, defeated.getBbHeight() * .45, 0);
        for (LumenButterfly butterfly : swarm) {
            butterfly.absorb(share);
            LumenExperienceParticle.flyTo(level, origin, butterfly, 6);
        }
        rewardOnAbsorb(owner, swarm, LumenColor.SILVER, MobEffects.SPEED);
        rewardOnAbsorb(owner, swarm, LumenColor.RED, MobEffects.STRENGTH);
        return 0;
    }

    private static int count(List<LumenButterfly> swarm, LumenColor color) {
        return (int) swarm.stream().filter(pet -> pet.hasPower(color)).count();
    }

    private static void rewardOnAbsorb(ServerPlayer owner, List<LumenButterfly> swarm, LumenColor color, Holder<MobEffect> effect) {
        int strength = count(swarm, color);
        if (strength > 0) {
            owner.addEffect(new MobEffectInstance(effect, HALF_MINUTE, strength - 1));
        }
    }

    static void playerTick(ServerPlayer owner) {
        if (!owner.isAlive() || owner.isSpectator()) {
            return;
        }
        if (owner.tickCount % 20 == 0) {
            LumenLedger.get(owner.level()).pay(owner);
        }
        recall(owner);
        List<LumenButterfly> swarm = followers(owner);
        if (swarm.isEmpty()) {
            return;
        }
        buyTimedEffect(owner, swarm, LumenColor.BLACK, MobEffects.NIGHT_VISION);
        buyTimedEffect(owner, swarm, LumenColor.BLUE, MobEffects.WATER_BREATHING);
        if (owner.tickCount % 20 == 0 && owner.getHealth() < owner.getMaxHealth()) {
            var existing = owner.getEffect(MobEffects.REGENERATION);
            if (existing == null || existing instanceof LumenRegeneration && existing.getDuration() <= 20) {
                int contributors = 0;
                for (LumenButterfly butterfly : swarm) {
                    if (butterfly.hasPower(LumenColor.PINK) && butterfly.spend(1)) {
                        contributors++;
                    }
                }
                if (contributors > 0) {
                    if (existing != null && existing.getAmplifier() != contributors - 1) {
                        owner.removeEffect(MobEffects.REGENERATION);
                    }
                    owner.addEffect(new LumenRegeneration(contributors - 1));
                }
            }
        }
    }

    private static void buyTimedEffect(ServerPlayer owner, List<LumenButterfly> swarm,
                                       LumenColor color, Holder<MobEffect> effect) {
        if (!owner.hasEffect(effect)) {
            List<LumenButterfly> pets = swarm.stream().filter(pet -> pet.hasPower(color)).toList();
            if (payShared(pets) > 0) {
                owner.addEffect(new MobEffectInstance(effect, HALF_MINUTE, 0));
            }
        }
    }

    private static int payShared(List<LumenButterfly> pets) {
        if (pets.isEmpty()) {
            return 0;
        }
        List<LumenButterfly> funded = pets.stream().filter(pet -> pet.storedXp() > 0)
                .sorted(java.util.Comparator.comparingDouble(LumenButterfly::storedXp)).toList();
        int first = 0;
        while (first < funded.size() && !funded.get(first).canSpend(1.0 / (funded.size() - first))) {
            first++;
        }
        if (first == funded.size()) {
            return 0;
        }
        double cost = 1.0 / (funded.size() - first);
        for (int i = first; i < funded.size(); i++) {
            funded.get(i).spend(cost);
        }
        return funded.size() - first;
    }

    static void changeDimension(ServerPlayer owner) {
        if (owner.isAlive() && !owner.isSpectator()) {
            recall(owner);
        }
    }

    private static void recall(ServerPlayer owner) {
        ServerLevel destination = owner.level();
        LumenLedger ledger = LumenLedger.get(destination);
        for (UUID pet : ledger.followers(owner.getUUID())) {
            if (destination.getEntity(pet) instanceof LumenButterfly butterfly) {
                if (butterfly.distanceToSqr(owner) > RANGE_SQUARED) {
                    bringToOwner(owner, ledger, butterfly);
                }
                continue;
            }
            LumenLedger.Location last = ledger.location(owner.getUUID(), pet);
            if (last == null) {
                // Saves from before location tracking can still recall pets in loaded chunks.
                if (owner.tickCount % 20 == 0) {
                    for (ServerLevel level : destination.getServer().getAllLevels()) {
                        if (level != destination && level.getEntity(pet) instanceof LumenButterfly butterfly) {
                            bringToOwner(owner, ledger, butterfly);
                            break;
                        }
                    }
                }
                continue;
            }

            ServerLevel source = destination.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, last.dimension()));
            if (source == null) {
                continue;
            }
            if (source.getEntity(pet) instanceof LumenButterfly butterfly) {
                bringToOwner(owner, ledger, butterfly);
            } else if (ledger.startRecall(owner.getUUID(), pet, destination.getGameTime())) {
                // A short entity-ticking ticket lets the real saved mob load before transfer.
                source.getChunkSource().addTicketWithRadius(LumenButterflies.RECALL_TICKET, ChunkPos.unpack(last.chunk()), 2);
            }
        }
    }

    private static void bringToOwner(ServerPlayer owner, LumenLedger ledger, LumenButterfly butterfly) {
        if (!butterfly.isAlive() || butterfly.isOrderedToSit() || !owner.getUUID().equals(butterfly.ownerId())) {
            return;
        }
        ServerLevel destination = owner.level();
        boolean water = owner.isInWater() && (butterfly.hasPower(LumenColor.BLUE) || hasBlue(owner));
        Vec3 target = LumenOrbit.nearOwner(destination, owner, butterfly, water);
        if (target == null) {
            return;
        }
        if (butterfly.level() != destination) {
            if (butterfly.teleport(new TeleportTransition(destination, target, Vec3.ZERO, owner.getYRot(),
                    0, TeleportTransition.DO_NOTHING)) instanceof LumenButterfly arrived) {
                ledger.remember(owner.getUUID(), arrived);
            }
            return;
        }
        butterfly.getNavigation().stop();
        butterfly.setDeltaMovement(Vec3.ZERO);
        butterfly.teleportTo(target.x, target.y, target.z);
        ledger.remember(owner.getUUID(), butterfly);
    }

    public static float shield(LivingEntity target, DamageSource source, float damage) {
        if (!(target instanceof LumenButterfly butterfly) || !(damage > 0) || !Float.isFinite(damage)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(butterfly.level() instanceof ServerLevel level)
                || butterfly.followSlot(level) < 0 || !(butterfly.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || butterfly.distanceToSqr(owner) > RANGE_SQUARED) {
            return damage;
        }

        double remaining = damage;
        for (UUID pet : LumenLedger.get(level).followers(owner.getUUID())) {
            if (level.getEntity(pet) instanceof LumenButterfly rainbow && rainbow.isAlive() && rainbow.isRainbow()
                    && !rainbow.isOrderedToSit() && owner.getUUID().equals(rainbow.ownerId())
                    && rainbow.distanceToSqr(owner) <= RANGE_SQUARED) {
                remaining -= rainbow.fundShield(remaining);
                if (remaining <= 0) {
                    return 0;
                }
            }
        }
        return (float) remaining;
    }

    public static boolean protectFromDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        long time = level.getGameTime();
        boolean protectedByLumen = entity instanceof LumenButterfly butterfly && butterfly.consumeTotem(time);
        if (entity instanceof ServerPlayer owner) {
            protectedByLumen = LumenLedger.get(level).useTotem(owner.getUUID(), followers(owner), time);
        }
        if (!protectedByLumen) {
            return false;
        }
        entity.setHealth(1);
        LumenButterflies.clearTotemEffects(entity);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800));
        level.broadcastEntityEvent(entity, (byte) 35);
        return true;
    }

    public static int spendEnchantmentBonus(ServerPlayer player) {
        int count = 0;
        for (LumenButterfly butterfly : followers(player)) {
            if (butterfly.hasPower(LumenColor.LIGHT_BLUE) && butterfly.spend(1)) {
                count++;
            }
        }
        return count;
    }

    private static final class LumenRegeneration extends MobEffectInstance {
        private LumenRegeneration(int amplifier) {
            super(MobEffects.REGENERATION, 21, amplifier);
        }
    }

    public static int regenerationClock(MobEffectInstance effect, LivingEntity entity, int vanillaDuration) {
        // A one-second renewal would never reach the first 50-tick vanilla regeneration threshold.
        return effect instanceof LumenRegeneration && vanillaDuration <= 21 ? entity.tickCount : vanillaDuration;
    }
}


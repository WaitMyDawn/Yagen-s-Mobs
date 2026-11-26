package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;

public final class KavatCombat {
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
    public static LivingEntity redirect(LivingEntity target) {
        return target instanceof Kavat k && k.hidden() && k.level() instanceof net.minecraft.server.level.ServerLevel level
                ? k.visibleDecoy(level) : target;
    }
    public static void damage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Kavat cat) || cat.level().isClientSide() || cat.decoy() || cat.genes().breed() != 1
                || amount <= 0 || REFLECTING.get()
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == cat
                || !attacker.isAlive() || !cat.getRandom().nextBoolean())
            return;
        REFLECTING.set(true);
        try {
            attacker.hurtServer((net.minecraft.server.level.ServerLevel)cat.level(), cat.damageSources().thorns(cat), 2 * amount);
        } finally {
            REFLECTING.remove();
        }
    }
    public static boolean rescue(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || player.getHealth() > 0
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return false;
        var cats = player.level().getEntities(yagen.waitmydawn.yagens_mobs.registries.ModEntities.KAVAT.get(),
                cat -> !cat.decoy() && cat.isAlive() && cat.isTame() && cat.isOwnedBy(player) && !cat.isOrderedToSit()
                        && cat.genes().breed() == 2 && cat.getHealth() > 1);
        var nearest = cats.stream().min(Comparator.comparingDouble(cat -> cat.distanceToSqr(player)));
        if (nearest.isEmpty()) return false;
        var cat = nearest.get();
        float paid = Math.min(cat.getMaxHealth() * .2F, cat.getHealth() - 1);
        cat.setHealth(cat.getHealth() - paid);
        player.setHealth(Math.min(player.getMaxHealth(), paid));
        player.level().playSound(null, player.blockPosition(), CreatureCue.KAVAT_RESCUE.orElse(SoundEvents.CAT_AMBIENT),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1F, 1F);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new KavatRescue());
        return true;
    }
}

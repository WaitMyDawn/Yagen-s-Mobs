package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.*;

public final class KavatCombat {
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
    public static void redirect(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Kavat k && k.hidden()
                && k.level() instanceof net.minecraft.server.level.ServerLevel level)
            event.setNewTarget(k.visibleDecoy(level));
    }
    public static void damage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof Kavat cat) || cat.level().isClientSide || cat.decoy() || cat.genes().breed() != 1
                || e.getAmount() <= 0 || REFLECTING.get()
                || !(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker == cat
                || !attacker.isAlive() || !cat.getRandom().nextBoolean())
            return;
        REFLECTING.set(true);
        try {
            attacker.hurt(cat.damageSources().thorns(cat), 2 * e.getAmount());
        } finally {
            REFLECTING.remove();
        }
    }
    public static void rescue(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.getHealth() > 0
                || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return;
        var cats = player.serverLevel().getEntities(yagen.waitmydawn.yagens_mobs.registries.ModEntities.KAVAT.get(),
                k
                -> !k.decoy() && k.isAlive() && k.isTame() && k.isOwnedBy(player) && !k.isOrderedToSit()
                        && k.genes().breed() == 2 && k.getHealth() > 1);
        cats.stream().min(Comparator.comparingDouble(k -> k.distanceToSqr(player))).ifPresent(cat -> {
            float paid = Math.min(cat.getMaxHealth() * .2F, cat.getHealth() - 1);
            cat.setHealth(cat.getHealth() - paid);
            player.setHealth(Math.min(player.getMaxHealth(), paid));
            e.setCanceled(true);
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.CAT_AMBIENT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1F, 1F);
            yagen.waitmydawn.yagens_mobs.network.LegacyNetwork.sendToPlayer(player, new KavatRescue());
        });
    }
}

package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

final class KavatMeleeGoal extends MeleeAttackGoal {
    private final Kavat cat;
    private long nextPounce;

    KavatMeleeGoal(Kavat cat) {
        super(cat, 1.25, true);
        this.cat = cat;
    }

    @Override
    public void tick() {
        LivingEntity target = cat.getTarget();
        if (cat.pouncing()) {
            if (!cat.onGround() && !cat.isInWater() && !cat.horizontalCollision && target != null) {
                cat.getLookControl().setLookAt(target, 30, 30);
                checkAndPerformAttack(target);
                return;
            }
            cat.setPouncing(false);
        }
        super.tick();
        if (!(target instanceof Enemy) || !cat.onGround() || cat.isInWater() || cat.isPassenger()
                || cat.isOrderedToSit() || !isTimeToAttack() || cat.level().getGameTime() < nextPounce)
            return;
        Vec3 offset = target.position().subtract(cat.position());
        double distance = offset.horizontalDistance();
        if (distance < 6 || distance > 10 || Math.abs(offset.y) > 1.5 || !cat.getSensing().hasLineOfSight(target))
            return;
        // Air travel retains about 91% of horizontal velocity per tick. Check the swept body
        // along the ballistic arc so a low ceiling or a fence does not repeatedly trigger a leap.
        nextPounce = cat.level().getGameTime() + 5;
        Vec3 velocity = new Vec3(offset.x / 8.8, .63 + offset.y * .055, offset.z / 8.8);
        var box = cat.getBoundingBox();
        Vec3 step = velocity;
        for (int i = 0; i < 18 && box.getCenter().subtract(target.position()).horizontalDistance() > 1.3; i++) {
            if (!cat.level().noCollision(cat, box.expandTowards(step))) return;
            box = box.move(step);
            step = step.multiply(.91, .98, .91).add(0, -.0784, 0);
        }
        cat.getNavigation().stop();
        cat.setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        cat.setPouncing(true);
        cat.setOnGround(false);
        cat.setDeltaMovement(velocity);
        cat.needsSync = true;
        nextPounce = cat.level().getGameTime() + 60;
    }

    @Override
    public void stop() {
        cat.setPouncing(false);
        super.stop();
    }
}

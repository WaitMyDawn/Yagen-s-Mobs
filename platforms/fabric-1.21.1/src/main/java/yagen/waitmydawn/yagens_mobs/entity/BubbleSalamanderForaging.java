package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.world.level.GameRules;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModTags;

final class BubbleSalamanderForaging {
    private BubbleSalamanderForaging() {
    }

    static final class HuntFish extends Goal {
        private final BubbleSalamander mob;
        @Nullable private LivingEntity prey;
        private int nextHunt;
        private int chaseUntil;
        private int nextPath;

        HuntFish(BubbleSalamander mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean canHunt() {
            return !mob.isTame() && !mob.isWorn() && !mob.isLeashed() && mob.isInWater();
        }

        private boolean isPrey(LivingEntity fish) {
            return fish.isAlive() && fish.isInWater() && fish.getType().is(ModTags.BUBBLE_SALAMANDER_PREY)
                    && !fish.hasCustomName() && (!(fish instanceof Bucketable captive) || !captive.fromBucket());
        }

        @Override
        public boolean canUse() {
            if (!canHunt() || mob.tickCount < nextHunt) {
                return false;
            }
            nextHunt = mob.tickCount + 55 + mob.getRandom().nextInt(30);
            prey = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(10, 4, 10), this::isPrey)
                    .stream().filter(mob::hasLineOfSight).min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            return prey != null;
        }

        @Override
        public void start() {
            chaseUntil = mob.tickCount + 160;
            nextPath = mob.tickCount;
        }

        @Override
        public boolean canContinueToUse() {
            return canHunt() && prey != null && isPrey(prey) && mob.tickCount < chaseUntil
                    && mob.distanceToSqr(prey) < 144;
        }

        @Override
        public void tick() {
            LivingEntity target = prey;
            if (target == null) {
                return;
            }
            if (mob.distanceToSqr(target) < 1.35 && mob.hasLineOfSight(target)) {
                if (mob.doHurtTarget(target) && !target.isAlive()) {
                    mob.heal(2);
                    mob.playSound(SoundEvents.GENERIC_EAT, .55F, 1.15F);
                    nextHunt = mob.tickCount + 300 + mob.getRandom().nextInt(140);
                }
                return;
            }
            if (mob.tickCount >= nextPath) {
                mob.getNavigation().moveTo(target, 1.45);
                nextPath = mob.tickCount + 12;
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            prey = null;
        }
    }

    static final class WaitingMeal extends Goal {
        private final BubbleSalamander mob;
        @Nullable private ItemEntity food;
        private int nextSearch;
        private int nextPath;
        private int endAt;

        WaitingMeal(BubbleSalamander mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean canForage() {
            return mob.isTame() && mob.isOrderedToSit() && !mob.isLeashed() && !mob.isWorn()
                    && mob.waitingPosition() != null;
        }

        private boolean isFood(ItemEntity item, Vec3 home) {
            return item.isAlive() && mob.isFood(item.getItem()) && item.distanceToSqr(home) <= 49;
        }

        @Override
        public boolean canUse() {
            if (!canForage() || mob.tickCount < nextSearch) {
                return false;
            }
            nextSearch = mob.tickCount + 25 + mob.getRandom().nextInt(15);
            Vec3 home = mob.waitingPosition();
            food = null;
            if (mob.distanceToSqr(home) > .36) {
                return true;
            }
            if (mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                food = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(7, 4, 7),
                                item -> isFood(item, home))
                        .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            }
            return food != null;
        }

        @Override
        public void start() {
            nextPath = mob.tickCount;
            endAt = mob.tickCount + 220;
        }

        @Override
        public boolean canContinueToUse() {
            return canForage() && mob.tickCount < endAt;
        }

        @Override
        public void tick() {
            Vec3 home = mob.waitingPosition();
            if (home == null) {
                endAt = mob.tickCount;
                return;
            }
            if (food != null && !isFood(food, home)) {
                food = null;
            }
            if (food != null && mob.distanceToSqr(food) <= 1.16 && mob.hasLineOfSight(food)) {
                if (mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    eat(food);
                }
                food = null;
                nextPath = mob.tickCount;
            }
            if (food == null && mob.distanceToSqr(home) <= .36) {
                // Snap the final fraction of a block only when the recorded waiting space is still clear.
                if (mob.level().noCollision(mob, mob.getBoundingBox().move(home.subtract(mob.position())))) {
                    mob.setPos(home);
                    mob.setDeltaMovement(Vec3.ZERO);
                }
                endAt = mob.tickCount;
                return;
            }
            if (mob.tickCount >= nextPath) {
                Vec3 target = food == null ? home : food.position();
                mob.getNavigation().moveTo(target.x, target.y, target.z, 1.25);
                nextPath = mob.tickCount + 12;
            }
        }

        private void eat(ItemEntity item) {
            ItemStack stack = item.getItem();
            ItemStack bite = stack.copyWithCount(1);
            if (stack.getCount() == 1) {
                item.discard();
            } else {
                item.setItem(stack.copyWithCount(stack.getCount() - 1));
            }
            mob.heal(4);
            mob.playSound(SoundEvents.GENERIC_EAT, .65F, 1.12F);
            if (mob.level() instanceof ServerLevel server) {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bite), mob.getX(), mob.getEyeY(),
                        mob.getZ(), 7, .18, .15, .18, .035);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            food = null;
            nextSearch = mob.tickCount + 35;
        }
    }
}

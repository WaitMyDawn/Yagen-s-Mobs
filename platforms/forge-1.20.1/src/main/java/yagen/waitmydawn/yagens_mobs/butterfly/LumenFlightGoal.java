package yagen.waitmydawn.yagens_mobs.butterfly;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.butterfly.mixin.LumenOrbAccess;

final class LumenFlightGoal extends Goal {
    private final LumenButterfly butterfly;
    private LumenOrbit orbit;
    private ExperienceOrb orb;
    private Vec3 wanderTarget;
    private int wanderAfter;
    private int navigateAfter;

    LumenFlightGoal(LumenButterfly butterfly) {
        this.butterfly = butterfly;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !butterfly.isBaby()
                || butterfly.level() instanceof ServerLevel level && butterfly.followSlot(level) >= 0
                && butterfly.getOwner() instanceof ServerPlayer owner && owner.isAlive()
                && !owner.isSpectator() && owner.isInWater() && LumenSwarm.hasBlue(owner);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        orb = null;
        wanderTarget = null;
        butterfly.setBubble(false);
        if (butterfly.isBaby()) {
            butterfly.setNoGravity(false);
        }
    }

    @Override
    public void tick() {
        if (!(butterfly.level() instanceof ServerLevel level) || !butterfly.isAlive()) {
            return;
        }
        int slot = butterfly.followSlot(level);
        ServerPlayer owner = slot >= 0 && butterfly.getOwner() instanceof ServerPlayer player && player.isAlive()
                && !player.isSpectator() ? player : null;
        boolean bubble = owner != null && owner.isInWater() && LumenSwarm.hasBlue(owner);
        butterfly.setBubble(bubble && (butterfly.isInWater() || owner.isUnderWater()));
        if (butterfly.isBaby()) {
            butterfly.setNoGravity(bubble);
            if (!bubble) {
                return;
            }
        }
        if (butterfly.mate() != null && butterfly.mate().isAlive()) {
            steer(level, butterfly.mate().position(), .8);
        } else if (owner != null) {
            follow(level, owner, bubble);
        } else if (!butterfly.isOrderedToSit() && (butterfly.isTame() || !seekExperience(level))) {
            wander(level);
        } else if (butterfly.isOrderedToSit()) {
            settle(level);
        }
    }

    private void follow(ServerLevel level, ServerPlayer owner, boolean bubble) {
        if (orbit == null) {
            orbit = new LumenOrbit(butterfly.getUUID());
        }
        Vec3 target = orbit.position(owner, level.getGameTime());
        if (!bubble && owner.isInWater()) {
            while (level.hasChunkAt(BlockPos.containing(target)) && level.getFluidState(BlockPos.containing(target)).is(FluidTags.WATER)
                    && target.y < owner.getY() + 6) {
                target = target.add(0, 1, 0);
            }
            if (!level.hasChunkAt(BlockPos.containing(target)) || level.getFluidState(BlockPos.containing(target)).is(FluidTags.WATER)) {
                return;
            }
        }
        if (clearPath(level, target) && !butterfly.horizontalCollision) {
            double speed = butterfly.position().distanceToSqr(target) > 4 ? .30 : .095;
            glideTo(target, speed);
        } else {
            steer(level, target, butterfly.distanceToSqr(owner) > 16 ? 1.35 : .65);
        }
    }

    private void glideTo(Vec3 target, double maxSpeed) {
        butterfly.getNavigation().stop();
        Vec3 offset = target.subtract(butterfly.position());
        Vec3 desired = offset.scale(Math.min(.23, maxSpeed / Math.max(offset.length(), .001)));
        butterfly.setDeltaMovement(butterfly.getDeltaMovement().lerp(desired, .56));
        if (!butterfly.isTame() && offset.horizontalDistanceSqr() > .09) {
            float yaw = (float) (Mth.atan2(-offset.x, offset.z) * Mth.RAD_TO_DEG);
            butterfly.setYRot(Mth.rotLerp(.32F, butterfly.getYRot(), yaw));
        }
    }

    private boolean seekExperience(ServerLevel level) {
        if ((orb == null || !orb.isAlive()) && (butterfly.tickCount + butterfly.getId()) % 10 == 0) {
            orb = level.getEntitiesOfClass(ExperienceOrb.class, butterfly.getBoundingBox().inflate(12), ExperienceOrb::isAlive)
                    .stream().min(Comparator.comparingDouble(butterfly::distanceToSqr)).orElse(null);
        }
        if (orb == null || !orb.isAlive() || butterfly.distanceToSqr(orb) > 196) {
            return false;
        }
        if (butterfly.distanceToSqr(orb) <= .45 && clearPath(level, orb.position())) {
            long amount = (long) orb.getValue() * ((LumenOrbAccess) orb).lumen$count();
            butterfly.absorb(amount * (butterfly.hasPower(LumenColor.GREEN) ? 2.0 : 1.0));
            LumenExperienceParticle.flyTo(level, orb.position().add(0, .12, 0), butterfly, 5);
            orb.discard();
            orb = null;
        } else {
            Vec3 target = orb.position().add(0, .04, 0);
            if (clearPath(level, target) && !butterfly.horizontalCollision) {
                glideTo(target, .24);
            } else {
                steer(level, target, 1.05);
            }
        }
        return true;
    }

    private void wander(ServerLevel level) {
        if (butterfly.tickCount >= wanderAfter) {
            wanderAfter = butterfly.tickCount + 46 + butterfly.getRandom().nextInt(54);
            Vec3 look = butterfly.getViewVector(1);
            wanderTarget = AirAndWaterRandomPos.getPos(butterfly, 6, 4, -1, look.x, look.z, 2.3);
            if (wanderTarget != null && !LumenOrbit.safe(level, butterfly, wanderTarget, false)) {
                wanderTarget = null;
            }
        }
        if (wanderTarget != null) {
            steer(level, wanderTarget, .70);
        }
    }

    private void settle(ServerLevel level) {
        if (butterfly.onGround()) {
            return;
        }
        BlockPos floor = butterfly.blockPosition();
        for (int depth = 0; depth <= 6 && level.getBlockState(floor).getCollisionShape(level, floor).isEmpty(); depth++) {
            floor = floor.below();
        }
        Vec3 target = Vec3.atBottomCenterOf(floor.above());
        if (target.y < butterfly.getY() && LumenOrbit.safe(level, butterfly, target, false)) {
            steer(level, target, .62);
        }
    }

    private boolean clearPath(ServerLevel level, Vec3 target) {
        return level.clip(new ClipContext(butterfly.position().add(0, .1, 0), target.add(0, .1, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, butterfly)).getType() == HitResult.Type.MISS;
    }

    private void steer(ServerLevel level, Vec3 target, double speed) {
        if (butterfly.position().distanceToSqr(target) < .10) {
            butterfly.getNavigation().stop();
            butterfly.setDeltaMovement(butterfly.getDeltaMovement().scale(.60));
            return;
        }
        if (clearPath(level, target) && !butterfly.horizontalCollision) {
            butterfly.getNavigation().stop();
            butterfly.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
        } else if (butterfly.tickCount >= navigateAfter) {
            navigateAfter = butterfly.tickCount + 14;
            butterfly.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }
}

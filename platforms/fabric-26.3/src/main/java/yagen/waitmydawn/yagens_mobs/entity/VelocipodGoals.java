package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.world.level.gamerules.GameRules;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

final class VelocipodGoals {
    private VelocipodGoals() {
    }

    private abstract static class FlightGoal extends Goal {
        final Velocipod pod;
        int retry;

        FlightGoal(Velocipod pod) {
            this.pod = pod;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            retry = 0;
            pod.getNavigation().stop();
        }

        boolean free() {
            return !pod.hasRider() && (pod.getTarget() == null || !pod.getTarget().isAlive());
        }

        boolean visible(Vec3 point) {
            return pod.level().clip(new ClipContext(pod.getEyePosition(), point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, pod)).getType() == HitResult.Type.MISS;
        }

        void flyTo(Vec3 point, double speed) {
            Vec3 forward = point.subtract(pod.position());
            Vec3 check = forward.scale(Math.min(1, .95 / Math.max(.95, forward.length())));
            if (visible(point.add(0, .28, 0)) && pod.level().noCollision(pod, pod.getBoundingBox().move(check))) {
                pod.getNavigation().stop();
                pod.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
            } else if (pod.tickCount >= retry) {
                pod.getNavigation().moveTo(point.x, point.y, point.z, speed);
                retry = pod.tickCount + 12;
            }
        }
    }

    static final class Hunt extends FlightGoal {
        Hunt(Velocipod pod) {
            super(pod);
        }

        @Override
        public boolean canUse() {
            return !pod.hasRider() && pod.getTarget() != null && pod.getTarget().isAlive();
        }

        @Override
        public void tick() {
            var target = pod.getTarget();
            if (target == null) {
                return;
            }
            pod.biteIfClose(target);

            // Aim above the prey's collision box so steering remains useful after making contact.
            Vec3 aim = target.position().add(0, target.getBbHeight() * .46 + .16, 0);
            flyTo(aim, target instanceof Slime && target.getType() == net.minecraft.world.entity.EntityTypes.SLIME ? 1.23 : .83);
        }
    }

    static final class Patrol extends FlightGoal {
        @Nullable private Vec3 waypoint;
        private int nextPatrol;
        private int timeout;

        Patrol(Velocipod pod) {
            super(pod);
        }

        @Override
        public boolean canUse() {
            if (!free() || pod.isBrooding() || pod.tickCount < nextPatrol) {
                return false;
            }
            nextPatrol = pod.tickCount + 105 + pod.getRandom().nextInt(170);
            BlockPos pos = pod.blockPosition().offset(pod.getRandom().nextInt(19) - 9, 0,
                    pod.getRandom().nextInt(19) - 9);
            if (!pod.level().hasChunkAt(pos)) {
                return false;
            }
            pos = pod.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)
                    .above(2 + pod.getRandom().nextInt(3));
            Vec3 candidate = Vec3.atBottomCenterOf(pos);
            if (!pod.level().noCollision(pod, pod.getDimensions(pod.getPose()).makeBoundingBox(candidate))) {
                return false;
            }
            waypoint = candidate;
            timeout = pod.tickCount + 70;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return waypoint != null && free() && !pod.isBrooding() && pod.tickCount < timeout
                    && pod.distanceToSqr(waypoint) > .8;
        }

        @Override
        public void tick() {
            if (waypoint != null) {
                flyTo(waypoint, .46);
            }
        }
    }

    static final class SugarCane extends FlightGoal {
        private final BlockState cane = Blocks.SUGAR_CANE.defaultBlockState();
        @Nullable private BlockPos target;
        private int nextScan;
        private int timeout;
        private boolean planting;

        SugarCane(Velocipod pod) {
            super(pod);
        }

        @Override
        public boolean canUse() {
            if (!free() || pod.isBrooding() || pod.tickCount < nextScan
                    || !((net.minecraft.server.level.ServerLevel) pod.level()).getGameRules().get(GameRules.MOB_GRIEFING)) {
                return false;
            }
            planting = pod.hasCaneToPlant();
            nextScan = pod.tickCount + (planting ? 8 : 55 + pod.getRandom().nextInt(35));
            
            var origin = pod.blockPosition();
            int radius = planting ? 5 : 9;
            target = null;
            for (var candidate : BlockPos.withinManhattan(origin, radius * 2 + 4)) {
                if (Math.abs(candidate.getY() - origin.getY()) <= 4
                        && Math.abs(candidate.getX() - origin.getX()) <= radius
                        && Math.abs(candidate.getZ() - origin.getZ()) <= radius
                        && (planting ? plantable(candidate) : harvestable(candidate))) {
                    target = candidate.immutable();
                    break;
                }
            }
            if (target == null && planting) {
                pod.setCaneToPlant(false);
            }
            timeout = pod.tickCount + 90;
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && free() && !pod.isBrooding() && pod.tickCount < timeout
                    && (planting ? plantable(target) : harvestable(target));
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(target);
            if (pod.distanceToSqr(center) < 2.25 * 2.25 && visible(center)) {
                if (((net.minecraft.server.level.ServerLevel) pod.level()).getGameRules().get(GameRules.MOB_GRIEFING)) {
                    if (planting) {
                        plant(target);
                    } else {
                        eat(target);
                    }
                }
                target = null;
                nextScan = pod.tickCount + (planting ? 100 : 4);
                return;
            }
            flyTo(Vec3.atBottomCenterOf(target).add(0, .38, 0), .62);
        }

        @Override
        public void stop() {
            super.stop();
            if (planting && pod.tickCount >= timeout) {
                pod.setCaneToPlant(false);
            }
            target = null;
        }

        private boolean plantable(BlockPos pos) {
            var level = pod.level();
            return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                    && level.isEmptyBlock(pos) && !level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                    && cane.canSurvive(level, pos);
        }

        private boolean harvestable(BlockPos pos) {
            var level = pod.level();
            return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                    && level.getBlockState(pos).is(Blocks.SUGAR_CANE)
                    && !level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                    && level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE);
        }

        private void eat(BlockPos root) {
            var level = pod.level();
            BlockPos tip = root.above();
            while (level.getBlockState(tip.above()).is(Blocks.SUGAR_CANE)) {
                tip = tip.above();
            }
            boolean ate = false;
            for (BlockPos pos = tip; pos.getY() > root.getY(); pos = pos.below()) {
                // Stop at a protected segment so removing a lower segment cannot break it indirectly.
                if (!((net.minecraft.server.level.ServerLevel) level).getGameRules().get(GameRules.MOB_GRIEFING)
                        || !level.removeBlock(pos, false)) {
                    break;
                }
                ate = true;
                pod.restoreFromCane();
            }
            if (ate) {
                pod.setCaneToPlant(true);
            }
        }

        private void plant(BlockPos pos) {
            if (pod.level() instanceof ServerLevel level && plantable(pos)) {
                level.setBlockAndUpdate(pos, cane);
            }
            pod.setCaneToPlant(false);
        }
    }

    static final class Brood extends FlightGoal {
        @Nullable private BlockPos site;
        private int nextSearch;
        private int reachDeadline;

        Brood(Velocipod pod) {
            super(pod);
        }

        @Override
        public boolean canUse() {
            return pod.isBrooding() && free();
        }

        @Override
        public void stop() {
            super.stop();
            site = null;
        }

        @Override
        public void tick() {
            // A running goal can tick again before the selector rechecks its continuation condition.
            if (!pod.isBrooding() || !free()) {
                return;
            }
            if (site == null || pod.tickCount % 10 == 0 && !safe(site)
                    || pod.tickCount >= reachDeadline && pod.distanceToSqr(Vec3.atBottomCenterOf(site)) > 1.15) {
                site = null;
                if (pod.tickCount < nextSearch) {
                    return;
                }
                site = chooseSite();
                nextSearch = pod.tickCount + 20;
                reachDeadline = pod.tickCount + 120;
            }
            if (site == null) {
                return;
            }
            Vec3 center = Vec3.atBottomCenterOf(site);
            if (pod.distanceToSqr(center) > .56 || !pod.onGround()) {
                if (pod.distanceToSqr(center) > .56) {
                    flyTo(center, .65);
                } else {
                    pod.getNavigation().stop();
                }
                return;
            }
            if (!safe(site)) {
                site = null;
                return;
            }
            pod.getNavigation().stop();
            if (pod.broodTicks() > 1) {
                pod.setBroodTicks(pod.broodTicks() - 1);
            } else if (birth(site)) {
                pod.completeBrood();
            } else {
                site = null;
            }
        }

        @Nullable
        private BlockPos chooseSite() {
            var level = pod.level();
            BlockPos current = pod.blockPosition();
            if (safe(current)) {
                return current;
            }
            for (int i = 0; i < 32; i++) {
                var candidate = current.offset(pod.getRandom().nextInt(25) - 12, 0,
                        pod.getRandom().nextInt(25) - 12);
                if (!level.hasChunkAt(candidate)) {
                    continue;
                }
                var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
                if (safe(surface)) {
                    return surface;
                }
            }
            return null;
        }

        private boolean safe(BlockPos pos) {
            var level = pod.level();
            return stable(pos) && level.noCollision(pod, pod.getDimensions(pod.getPose())
                    .makeBoundingBox(Vec3.atBottomCenterOf(pos)))
                    && !level.containsAnyLiquid(pod.getDimensions(pod.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos)))
                    && level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(pos).inflate(7),
                    mob -> mob instanceof Enemy && mob.isAlive()).isEmpty();
        }

        private boolean stable(BlockPos pos) {
            var level = pod.level();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty()) {
                return false;
            }
            BlockState support = level.getBlockState(pos.below());
            return support.isFaceSturdy(level, pos.below(), Direction.UP)
                    && !support.is(Blocks.MAGMA_BLOCK) && !support.is(Blocks.CACTUS)
                    && !support.is(Blocks.CAMPFIRE) && !support.is(Blocks.SOUL_CAMPFIRE)
                    && !level.getBlockState(pos).is(Blocks.FIRE) && !level.getBlockState(pos).is(Blocks.SOUL_FIRE);
        }

        private boolean birth(BlockPos origin) {
            if (!(pod.level() instanceof ServerLevel level)) {
                return false;
            }
            var child = ModEntities.VELOCIPOD.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (child == null) {
                return false;
            }
            child.setBaby(true);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos place = origin.relative(side, 2);
                if (!stable(place)) {
                    continue;
                }
                var feet = Vec3.atBottomCenterOf(place);
                var box = child.getDimensions(child.getPose()).makeBoundingBox(feet);
                if (level.noCollision(child, box) && !level.containsAnyLiquid(box)) {
                    child.setColor(pod.getRandom().nextDouble() < .9 ? pod.color()
                            : Velocipod.Color.random(pod.getRandom()));
                    child.snapTo(feet.x, feet.y, feet.z, pod.getYRot(), 0);
                    return level.addFreshEntity(child);
                }
            }
            return false;
        }
    }
}


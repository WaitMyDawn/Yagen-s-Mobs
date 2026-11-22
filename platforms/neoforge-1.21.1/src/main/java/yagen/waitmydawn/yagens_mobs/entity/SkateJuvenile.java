package yagen.waitmydawn.yagens_mobs.entity;

import java.util.Comparator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class SkateJuvenile {
    private static final double FOLLOW_RANGE = 16.0;
    private static final double KEEP_LEADER_RANGE = 24.0;
    private final DesertSkate skate;
    private final SkateBurrow burrow;
    @Nullable private DesertSkate leader;
    private int nextLeaderScan;
    private int nextPathUpdate;
    private boolean following;

    SkateJuvenile(DesertSkate skate, SkateBurrow burrow) {
        this.skate = skate;
        this.burrow = burrow;
    }

    boolean tick() {
        findLeader();
        boolean inDanger = leader != null && leader.getTarget() != null && leader.getTarget().isAlive();
        boolean leaderBuried = leader != null && leader.isBurrowed();
        boolean stayBuried = !skate.level().isRaining() && (leaderBuried || inDanger);

        if (skate.isBurrowed()) {
            if (stayBuried) {
                Vec3 shelter = leader.position().add(0.0, leaderBuried ? 0.0 : -SkateBurrow.SUBMERGED_OFFSET, 0.0);
                if (skate.position().distanceToSqr(shelter) > 0.16) {
                    burrow.swim(shelter);
                }
            } else {
                Vec3 exit = burrow.surface();
                if (exit != null) {
                    burrow.emerge(exit);
                } else {
                    burrow.swim(leader == null ? null : leader.position());
                }
            }
            return true;
        }
        if (skate.burrowPhase() != DesertSkate.BurrowPhase.SURFACE) {
            return true;
        }
        if (stayBuried && burrow.enter(!inDanger)) {
            following = false;
            return true;
        }
        if (skate.isEating()) {
            return true;
        }
        if (leader == null) {
            stopFollowing();
            return false;
        }

        Vec3 behind = leader.position().add(Vec3.directionFromRotation(0.0F, leader.getYRot()).scale(-1.4));
        if (leaderBuried) {
            Vec3 exit = leader.burrowSurface();
            if (exit != null) {
                behind = new Vec3(behind.x, exit.y, behind.z);
            }
        }
        if (skate.distanceToSqr(behind) > 1.0) {
            if (skate.tickCount >= nextPathUpdate) {
                skate.getNavigation().moveTo(behind.x, behind.y, behind.z, inDanger ? 1.3 : 1.15);
                nextPathUpdate = skate.tickCount + 8;
            }
            following = true;
            return true;
        }
        stopFollowing();
        return inDanger;
    }

    private void stopFollowing() {
        if (following) {
            skate.getNavigation().stop();
            following = false;
        }
    }

    private void findLeader() {
        if (leader != null && leader.isAlive() && !leader.isBaby()
                && skate.distanceToSqr(leader) <= KEEP_LEADER_RANGE * KEEP_LEADER_RANGE) {
            return;
        }
        leader = null;
        if (skate.tickCount < nextLeaderScan) {
            return;
        }
        nextLeaderScan = skate.tickCount + 12;
        leader = skate.level().getEntitiesOfClass(DesertSkate.class, skate.getBoundingBox().inflate(FOLLOW_RANGE, 6.0, FOLLOW_RANGE),
                        adult -> !adult.isBaby() && adult.isAlive() && skate.distanceToSqr(adult) <= FOLLOW_RANGE * FOLLOW_RANGE)
                .stream().min(Comparator.comparingDouble(skate::distanceToSqr)).orElse(null);
    }
}

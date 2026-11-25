package yagen.waitmydawn.yagens_mobs.mixin;

import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
abstract class SlimeFlightAvoidance extends MoveControl<AbstractCubeMob> {
    protected SlimeFlightAvoidance(AbstractCubeMob mob) {
        super(mob);
    }

    @Shadow public abstract void setDirection(float yaw, boolean aggressive);
    @Shadow public abstract void setWantedMovement(double speed);
    @Unique private static final float[] YAGENS_ESCAPE_TURNS = {0, 32, -32, 68, -68};
    @Unique @Nullable private Velocipod yagensMobs$predator;
    @Unique private int yagensMobs$scanAt;

    @Inject(method = "tick", at = @At("HEAD"))
    private void yagensMobs$fleeVelocipod(CallbackInfo callback) {
        if (!(mob instanceof Slime slime) || slime.getType() != EntityTypes.SLIME
                || slime.level().isClientSide() || slime.isPassenger()) {
            return;
        }
        if (slime.tickCount >= yagensMobs$scanAt) {
            yagensMobs$scanAt = slime.tickCount + 8 + (slime.getId() & 3);
            yagensMobs$predator = null;
            double nearest = 16 * 16;
            for (var pod : slime.level().getEntitiesOfClass(Velocipod.class, slime.getBoundingBox().inflate(16),
                    Velocipod::isAlive)) {
                double distance = slime.distanceToSqr(pod);
                if (distance <= nearest) {
                    nearest = distance;
                    yagensMobs$predator = pod;
                }
            }
        }
        var threat = yagensMobs$predator;
        if (threat == null || !threat.isAlive() || threat.level() != slime.level() || slime.distanceToSqr(threat) > 256) {
            return;
        }

        Vec3 away = slime.position().subtract(threat.position()).multiply(1, 0, 1);
        if (away.horizontalDistanceSqr() < 1.e-6) {
            away = Vec3.directionFromRotation(0, slime.getYRot());
        }
        float heading = (float) (Mth.atan2(-away.x, away.z) * Mth.RAD_TO_DEG);
        for (float offset : YAGENS_ESCAPE_TURNS) {
            Vec3 step = Vec3.directionFromRotation(0, heading + offset).scale(.9);
            if (slime.level().noCollision(slime, slime.getBoundingBox().move(step))) {
                heading += offset;
                break;
            }
        }
        // Slimes ignore regular navigation steering. Keep the jumps native and replace their heading.
        setDirection(heading, true);
        setWantedMovement(1.12);
    }
}


package yagen.waitmydawn.yagens_mobs.entity.desertskate;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SkateSting {
    static final int WINDUP_TICKS = 10;
    static final int TOTAL_TICKS = WINDUP_TICKS + 7;
    private static final float BASE_OFFSET = 7.0F;
    private static final float BASE_LENGTH = 6.0F;
    private static final float MID_LENGTH = 5.0F;
    private static final float TIP_LENGTH = 4.0F;
    private static final float BASE_BEND = 0.74F;
    private static final float MID_BEND = 0.93F;
    private static final float TIP_BEND = 1.15F;

    private SkateSting() {
    }

    static Vec3 muzzle(DesertSkate shooter, float bodyYaw) {
        // The pivots and bends match the sting clip at 0.5 s, when the server launches the spike.
        double up = BASE_LENGTH * Mth.sin(BASE_BEND)
                + MID_LENGTH * Mth.sin(BASE_BEND + MID_BEND) + TIP_LENGTH * Mth.sin(BASE_BEND + MID_BEND + TIP_BEND);
        double back = BASE_OFFSET + BASE_LENGTH * Mth.cos(BASE_BEND)
                + MID_LENGTH * Mth.cos(BASE_BEND + MID_BEND) + TIP_LENGTH * Mth.cos(BASE_BEND + MID_BEND + TIP_BEND);
        double scale = shooter.getScale() / 16.0;
        Vec3 behind = Vec3.directionFromRotation(0.0F, bodyYaw).scale(-back * scale);
        return shooter.position().add(behind).add(0.0, (4.0 + up) * scale, 0.0);
    }
}

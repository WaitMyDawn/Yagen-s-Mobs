package yagen.waitmydawn.yagens_mobs.entity.velocipod.client;

import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

/** Per-rider presentation clock shared by body, armor and world-space rotation passes. */
public final class RiderMountMotion {
    private static final WeakHashMap<Player, RiderMountMotion> RIDERS = new WeakHashMap<>();
    private static final float BOARD_TICKS = 9;
    private static final float RELEASE_TICKS = 7;

    private boolean mounted;
    private double changedAt;
    private float start;
    private float heading;
    private final Quaternionf tilt = new Quaternionf();

    private RiderMountMotion() {}

    public static RiderMountMotion sample(Player player, float partialTick) {
        var pod = player.getVehicle() instanceof Velocipod insect ? insect : null;
        if (!player.isAlive() || (pod == null && (player.isPassenger() || player.isSwimming() || player.isSleeping()))) {
            RIDERS.remove(player);
            return null;
        }
        var motion = RIDERS.get(player);
        if (motion == null) {
            if (pod == null) return null;
            motion = new RiderMountMotion();
            RIDERS.put(player, motion);
        }
        double now = player.tickCount + (double) partialTick;
        if (motion.mounted != (pod != null)) {
            motion.start = motion.weight(now);
            motion.changedAt = now;
            motion.mounted = pod != null;
        }
        if (pod != null) {
            motion.heading = Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot());
            motion.tilt.set(pod.riderTilt(partialTick));
        } else if (motion.weight(now) <= 0) {
            RIDERS.remove(player);
            return null;
        }
        return motion;
    }

    public float weight(double now) {
        float t = Mth.clamp((float)(now - changedAt) / (mounted ? BOARD_TICKS : RELEASE_TICKS), 0, 1);
        return Mth.lerp(t * t * (3 - 2 * t), start, mounted ? 1 : 0);
    }

    public float heading() { return heading + 52; }

    public Quaternionf rotation(float yaw, float weight) {
        var standing = new Quaternionf().rotationY((180 - yaw) * Mth.DEG_TO_RAD);
        var riding = new Quaternionf().rotationY((180 - heading) * Mth.DEG_TO_RAD)
                .mul(tilt).rotateY(-52 * Mth.DEG_TO_RAD);
        return standing.slerp(riding, weight);
    }
}

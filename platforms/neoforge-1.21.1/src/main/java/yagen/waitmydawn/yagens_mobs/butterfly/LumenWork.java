package yagen.waitmydawn.yagens_mobs.butterfly;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LumenWork {
    private static final double COST_PER_TICK = 1.0 / 80;
    private static final ThreadLocal<Boolean> EXTRA_TICK = ThreadLocal.withInitial(() -> false);

    private LumenWork() {
    }

    public interface Progress {
        boolean lumen$isWorking();
    }

    public static void advance(ServerLevel level, BlockPos pos, Progress progress, Runnable simulateTick) {
        if (EXTRA_TICK.get() || !progress.lumen$isWorking()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        var helpers = level.getEntitiesOfClass(LumenButterfly.class, new AABB(pos).inflate(3),
                pet -> pet.isAlive() && !pet.isBaby() && pet.hasPower(LumenColor.BROWN)
                        && pet.distanceToSqr(center) <= 9 && pet.canSpend(COST_PER_TICK));
        if (helpers.isEmpty()) {
            return;
        }
        EXTRA_TICK.set(true);
        try {
            for (LumenButterfly helper : helpers) {
                if (!progress.lumen$isWorking()) {
                    break;
                }
                if (helper.spend(COST_PER_TICK)) {
                    simulateTick.run();
                }
            }
        } finally {
            EXTRA_TICK.remove();
        }
    }
}

package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;

public final class SalvagePlayerMotion {
    private record Sample(ResourceKey<Level> dimension, Vec3 position, Vec3 displacement) {}

    private static final Map<ServerPlayer, Sample> SAMPLES = new WeakHashMap<>();

    private SalvagePlayerMotion() {}

    static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            var old = SAMPLES.get(player);
            Vec3 position = player.position();
            Vec3 displacement = old == null || !old.dimension().equals(player.level().dimension()) ? Vec3.ZERO : position.subtract(old.position());
            SAMPLES.put(player, new Sample(player.level().dimension(), position, displacement.lengthSqr() < 64 ? displacement : Vec3.ZERO));
        }
    }

    static Vec3 movement(Entity entity) {
        var sample = SAMPLES.get(entity);
        return sample == null ? entity.getDeltaMovement() : sample.displacement();
    }

    public static int viewDistance(ServerPlayer player) {
        return player instanceof ViewDistance distance ? distance.yagensMobs$viewDistance() : 2;
    }

    public interface ViewDistance {
        int yagensMobs$viewDistance();
    }
}

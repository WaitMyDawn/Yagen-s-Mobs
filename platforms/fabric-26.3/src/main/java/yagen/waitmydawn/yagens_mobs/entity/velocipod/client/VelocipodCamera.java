package yagen.waitmydawn.yagens_mobs.entity.velocipod.client;

import net.minecraft.client.Camera;

import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

public final class VelocipodCamera {
    private static BooleanSupplier nfpShoulderView = () -> false;

    private VelocipodCamera() {
    }

    public static float addRearDistance(Camera camera, float distance) {
        Minecraft game = Minecraft.getInstance();
        if (camera == game.gameRenderer.mainCamera() && camera.entity() == game.player
                && !nfpShoulderView.getAsBoolean() && game.player != null) {
            return distance + (float) extraDistance() * game.player.getScale();
        }
        return distance;
    }

    public static void registerNfpShoulderView(BooleanSupplier active) {
        nfpShoulderView = active;
    }

    public static double nfpScaledExtraDistance() {
        var player = Minecraft.getInstance().player;
        return player != null && nfpShoulderView.getAsBoolean() ? extraDistance() * player.getScale() : 0;
    }

    public static double extraDistance() {
        Minecraft game = Minecraft.getInstance();
        if (game.options.getCameraType() != CameraType.THIRD_PERSON_BACK || game.player == null
                || game.getCameraEntity() != game.player || !(game.player.getVehicle() instanceof Velocipod pod)) {
            return 0;
        }
        float partialTick = game.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return ClientConfigs.VELOCIPOD_MAX_CAMERA_PULLBACK.get() * pod.riderSpeedFraction(partialTick);
    }
}

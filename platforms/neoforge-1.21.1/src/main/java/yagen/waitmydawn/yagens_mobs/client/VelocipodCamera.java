package yagen.waitmydawn.yagens_mobs.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class VelocipodCamera {
    private static BooleanSupplier nfpShoulderView = () -> false;

    private VelocipodCamera() {
    }

    @SubscribeEvent
    public static void pullBack(CalculateDetachedCameraDistanceEvent event) {
        Minecraft game = Minecraft.getInstance();
        if (!event.isCameraFlipped() && event.getCamera() == game.gameRenderer.getMainCamera()
                && event.getCamera().getEntity() == game.player && !nfpShoulderView.getAsBoolean()) {
            event.setDistance(event.getDistance() + (float) extraDistance());
        }
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
                || game.getCameraEntity() != game.player || !(game.player.getVehicle() instanceof Velocipod pod)
                || !ClientConfigs.SPEC.isLoaded()) {
            return 0;
        }
        float partialTick = game.getTimer().getGameTimeDeltaPartialTick(true);
        return ClientConfigs.VELOCIPOD_MAX_CAMERA_PULLBACK.get() * pod.riderSpeedFraction(partialTick);
    }
}

package yagen.waitmydawn.yagens_mobs.entity.velocipod.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class VelocipodCamera {
    private static BooleanSupplier nfpShoulderView = () -> false;

    private VelocipodCamera() {
    }


    public static void registerNfpShoulderView(BooleanSupplier active) {
        nfpShoulderView = active;
    }

    public static double nfpScaledExtraDistance() {
        var player = Minecraft.getInstance().player;
        return player != null && nfpShoulderView.getAsBoolean() ? extraDistance() : 0;
    }

    public static double vanillaExtraDistance() {
        return nfpShoulderView.getAsBoolean() ? 0 : extraDistance();
    }

    public static double extraDistance() {
        Minecraft game = Minecraft.getInstance();
        if (game.options.getCameraType() != CameraType.THIRD_PERSON_BACK || game.player == null
                || game.getCameraEntity() != game.player || !(game.player.getVehicle() instanceof Velocipod pod)
                || !ClientConfigs.SPEC.isLoaded()) {
            return 0;
        }
        float partialTick = game.getFrameTime();
        return ClientConfigs.VELOCIPOD_MAX_CAMERA_PULLBACK.get() * pod.riderSpeedFraction(partialTick);
    }
}

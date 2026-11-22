package yagen.waitmydawn.yagens_mobs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ClientConfigs {
    private static final FabricConfig.Value<Boolean> TRAILS = FabricConfig.flag(
            "sandTrails", true, "Show the sand trail of a burrowing Desert Skate.");
    private static final FabricConfig.Value<Double> PULLBACK = FabricConfig.decimal(
            "velocipodMaxCameraPullback", 4, 0, 16, "Maximum extra rear camera distance while riding a Velocipod.");
    private static volatile CommentedConfig active = CommentedConfig.inMemory();
    public static final Supplier<Boolean> SAND_TRAILS = () -> TRAILS.value(active);
    public static final Supplier<Double> VELOCIPOD_MAX_CAMERA_PULLBACK = () -> PULLBACK.value(active);

    private ClientConfigs() {
    }

    public static void reload() {
        var path = FabricLoader.getInstance().getConfigDir().resolve(ModIdentity.CLIENT_CONFIG);
        active = FabricConfig.load(path, List.of(TRAILS, PULLBACK));
    }
}

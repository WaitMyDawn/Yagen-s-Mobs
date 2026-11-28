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
    private static final String DEFAULT_KAVAT_ORDER = "breed,coat,ear,tail";
    private static final FabricConfig.Value<String> KAVAT_ORDER = new FabricConfig.Value<>(
            "kavatGuideSortOrder", DEFAULT_KAVAT_ORDER, value ->
                    value instanceof String s && java.util.Arrays.stream(s.split(",", -1))
                    .map(String::trim).collect(java.util.stream.Collectors.toSet())
                    .equals(java.util.Set.of("breed", "coat", "ear", "tail"))
                    && s.split(",", -1).length == 4 ? s : DEFAULT_KAVAT_ORDER,
            "Field guide dictionary sort. Use breed, coat, ear and tail once each, separated by commas.");
    private static volatile CommentedConfig active = CommentedConfig.inMemory();
    public static final Supplier<Boolean> SAND_TRAILS = () -> TRAILS.value(active);
    public static final Supplier<Double> VELOCIPOD_MAX_CAMERA_PULLBACK = () -> PULLBACK.value(active);

    public static final Supplier<String> KAVAT_SORT_ORDER = () -> KAVAT_ORDER.value(active);

    private ClientConfigs() {
    }

    public static void reload() {
        var path = FabricLoader.getInstance().getConfigDir().resolve(ModIdentity.CLIENT_CONFIG);
        active = FabricConfig.load(path, List.of(TRAILS, PULLBACK, KAVAT_ORDER));
    }
}

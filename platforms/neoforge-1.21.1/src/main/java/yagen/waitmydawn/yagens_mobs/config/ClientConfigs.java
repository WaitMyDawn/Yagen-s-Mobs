package yagen.waitmydawn.yagens_mobs.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfigs {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SAND_TRAILS = BUILDER
            .comment("Show the sand trail of a burrowing Desert Skate near the surface.")
            .define("sandTrails", true);
    public static final ModConfigSpec.DoubleValue VELOCIPOD_MAX_CAMERA_PULLBACK = BUILDER
            .comment("Maximum extra rear camera distance when riding a Velocipod at sprint speed. Set to 0 to disable.")
            .defineInRange("velocipodMaxCameraPullback", 4.0, 0.0, 16.0);
    public static final ModConfigSpec.ConfigValue<String> KAVAT_SORT_ORDER = BUILDER
            .comment("Field guide dictionary sort. Use breed, coat, ear and tail once each, separated by commas.")
            .define("kavatGuideSortOrder", "breed,coat,ear,tail", value -> {
                if (!(value instanceof String s)) return false;
                var fields = java.util.Arrays.stream(s.split(",", -1)).map(String::trim).toList();
                return fields.size() == 4 && fields.containsAll(java.util.List.of("breed","coat","ear","tail"));
            });
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfigs() {
    }
}

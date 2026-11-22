package yagen.waitmydawn.yagens_mobs.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfigs {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue SAND_TRAILS = BUILDER
            .comment("Show the sand trail of a burrowing Desert Skate near the surface.")
            .define("sandTrails", true);
    public static final ForgeConfigSpec.DoubleValue VELOCIPOD_MAX_CAMERA_PULLBACK = BUILDER
            .comment("Maximum extra rear camera distance when riding a Velocipod at sprint speed. Set to 0 to disable.")
            .defineInRange("velocipodMaxCameraPullback", 4.0, 0.0, 16.0);
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfigs() {
    }
}

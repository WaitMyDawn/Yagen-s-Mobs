package yagen.waitmydawn.yagens_mobs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SpawnDefaults;

public final class ServerConfigs {
    private static volatile CommentedConfig active = CommentedConfig.inMemory();
    private static final List<FabricConfig.Value<?>> VALUES = new ArrayList<>();
    public static final Spawns DESERT_SKATE = spawns(SpawnDefaults.DESERT_SKATE);
    public static final Spawns VULTURE = spawns(SpawnDefaults.CONDROC);
    public static final Spawns ROCK_HANGER = spawns(SpawnDefaults.ROCK_HANGER);
    public static final Spawns BUBBLE_SALAMANDER = spawns(SpawnDefaults.BUBBLE_SALAMANDER);
    public static final Spawns VELOCIPOD = spawns(SpawnDefaults.VELOCIPOD);
    public static final Spawns LUMEN_BUTTERFLY = spawns(SpawnDefaults.LUMEN_BUTTERFLY);
    public static final Supplier<Integer> ROCK_HANGER_CEILING_CLEARANCE = register(FabricConfig.integer(
            "spawns.rock_hanger.minimumAirBelow", SpawnDefaults.ROCK_HANGER_CEILING_CLEARANCE, 16, 256,
            "Minimum uninterrupted clearance below the Rock Hanger's ceiling."));
    public static final Supplier<Double> DESERT_SKATE_JUVENILE_CHANCE = register(FabricConfig.decimal(
            "spawns.desert_skate.juvenileChance", SpawnDefaults.DESERT_SKATE_JUVENILE_CHANCE, 0, 1,
            "Chance that each naturally spawned group member after the first is a juvenile."));
    public static final Supplier<Double> VULTURE_JUVENILE_CHANCE = register(FabricConfig.decimal(
            "spawns.condroc.juvenileChance", SpawnDefaults.CONDROC_JUVENILE_CHANCE, 0, 1,
            "Chance that a naturally spawned Condroc is a juvenile."));

    private ServerConfigs() {
    }

    private static <T> Supplier<T> register(FabricConfig.Value<T> definition) {
        VALUES.add(definition);
        return () -> definition.value(active);
    }

    private static Spawns spawns(SpawnDefaults defaults) {
        String prefix = "spawns." + defaults.path() + ".";
        return new Spawns(register(FabricConfig.flag(prefix + "enabled", true, "Enable natural spawning.")),
                register(FabricConfig.integer(prefix + "weight", defaults.weight(), 0, 1000, "Relative spawn weight.")),
                register(FabricConfig.integer(prefix + "minGroupSize", defaults.minGroupSize(), 1, 16, "Minimum group size.")),
                register(FabricConfig.integer(prefix + "maxGroupSize", defaults.maxGroupSize(), 1, 16, "Maximum group size.")));
    }

    public static void reload(MinecraftServer server) {
        var global = FabricLoader.getInstance().getConfigDir().resolve(ModIdentity.SERVER_CONFIG);
        if (server == null) {
            active = FabricConfig.load(global, VALUES);
        } else {
            var world = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(ModIdentity.SERVER_CONFIG);
            active = FabricConfig.load(Files.isRegularFile(world) ? world : global, VALUES);
        }
    }

    public record Spawns(Supplier<Boolean> enabled, Supplier<Integer> weight, Supplier<Integer> minGroupSize,
                         Supplier<Integer> maxGroupSize) {
        public boolean canSpawn() {
            return enabled.get() && weight.get() > 0;
        }

        public int maxGroupCount() {
            return Math.max(minGroupSize.get(), maxGroupSize.get());
        }
    }
}

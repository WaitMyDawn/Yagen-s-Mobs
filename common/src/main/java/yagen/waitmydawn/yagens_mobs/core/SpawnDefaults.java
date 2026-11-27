package yagen.waitmydawn.yagens_mobs.core;

public enum SpawnDefaults {
    DESERT_SKATE("desert_skate", 2, 3, 4),
    CONDROC("condroc", 8, 1, 3),
    ROCK_HANGER("rock_hanger", 10, 1, 1),
    BUBBLE_SALAMANDER("bubble_salamander", 5, 1, 2),
    VELOCIPOD("velocipod", 6, 1, 2),
    LUMEN_BUTTERFLY("lumen_butterfly", 8, 2, 4),
    SNOWCACHE_WEASEL("snowcache_weasel", 6, 1, 2),
    COLLECTOR("collector", 1, 1, 1),
    KAVAT("kavat", 10, 1, 4);

    public static final double DESERT_SKATE_JUVENILE_CHANCE = 0.25;
    public static final double CONDROC_JUVENILE_CHANCE = 0.20;
    public static final int ROCK_HANGER_CEILING_CLEARANCE = 16;

    private final String path;
    private final int weight;
    private final int minGroupSize;
    private final int maxGroupSize;

    SpawnDefaults(String path, int weight, int minGroupSize, int maxGroupSize) {
        this.path = path;
        this.weight = weight;
        this.minGroupSize = minGroupSize;
        this.maxGroupSize = maxGroupSize;
    }

    public String path() {
        return path;
    }

    public int weight() {
        return weight;
    }

    public int minGroupSize() {
        return minGroupSize;
    }

    public int maxGroupSize() {
        return maxGroupSize;
    }

    public static int scaledWeight(int weight, double factor) {
        return weight == 0 ? 0 : Math.max(1, (int) Math.round(weight * factor));
    }
}

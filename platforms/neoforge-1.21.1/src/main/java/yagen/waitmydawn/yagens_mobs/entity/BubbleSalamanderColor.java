package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.util.RandomSource;

public enum BubbleSalamanderColor {
    GREEN("green", 2), ALBINO("albino", 1), PINK("pink", 2), YELLOW("yellow", 2),
    BLACK("black", 2), BROWN("brown", 2), RED("red", 2), BLUE("blue", 2), MIXED("mixed", 1);

    public static final int LEGACY_MIXED_PATTERNS = 32;
    private static final BubbleSalamanderColor[] COLORS = values();
    private static final int TOTAL_WEIGHT = java.util.Arrays.stream(COLORS).mapToInt(color -> color.weight).sum();
    private final String id;
    private final int weight;

    BubbleSalamanderColor(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    static BubbleSalamanderColor byId(String id) {
        for (BubbleSalamanderColor color : COLORS) {
            if (color.id.equals(id)) {
                return color;
            }
        }
        return GREEN;
    }

    static BubbleSalamanderColor byIndex(int index) {
        return index >= 0 && index < COLORS.length ? COLORS[index] : GREEN;
    }

    static BubbleSalamanderColor roll(RandomSource random) {
        int pick = random.nextInt(TOTAL_WEIGHT);
        for (BubbleSalamanderColor color : COLORS) {
            pick -= color.weight;
            if (pick < 0) {
                return color;
            }
        }
        return GREEN;
    }

    static Coat randomCoat(RandomSource random) {
        BubbleSalamanderColor color = roll(random);
        // Nonnegative indices below LEGACY_MIXED_PATTERNS identify the original baked textures.
        return new Coat(color, color == MIXED ? random.nextLong() | Long.MIN_VALUE : 0L);
    }

    record Coat(BubbleSalamanderColor color, long seed) {
        static Coat inherit(RandomSource random, Coat mother, Coat father) {
            int roll = random.nextInt(100);
            if (roll < 90) {
                return mother;
            }
            return roll < 99 ? father : randomCoat(random);
        }
    }
}

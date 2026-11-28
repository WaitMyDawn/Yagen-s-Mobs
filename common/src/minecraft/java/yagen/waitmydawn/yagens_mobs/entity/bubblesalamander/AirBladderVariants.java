package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

public final class AirBladderVariants {
    private AirBladderVariants() {}

    public static String path(BubbleSalamanderColor color) {
        return switch (color) {
            case ALBINO -> "white_air_bladder";
            case MIXED -> "rainbow_air_bladder";
            default -> color.id() + "_air_bladder";
        };
    }
}

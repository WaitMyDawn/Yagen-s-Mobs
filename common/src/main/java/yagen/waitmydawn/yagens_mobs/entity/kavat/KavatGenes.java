package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Trait positions are persisted in four fixed bytes. Extend each ring only at the end. */
public record KavatGenes(int breed, int coat, int ear, int tail) {
    public static final List<String> BREEDS = List.of("smeeta", "adarza", "vasca");
    public static final List<String> COATS =
            List.of("copper_brown", "iron_gray", "gold", "diamond_blue", "emerald", "lapis", "redstone", "snow_white",
                    "wither_black", "dragon_breath_purple", "chorus_flower_white", "spore_blossom_pink",
                    "magma_red", "shroomlight_orange", "cherry_pink", "desert_yellow");
    public static final List<String> EARS = List.of("tufted", "long", "medium", "folded");
    public static final List<String> TAILS = List.of("phoenix", "fan", "fish", "leaf", "arrow");

    public KavatGenes {
        if (breed < 0 || breed >= BREEDS.size() || coat < 0 || coat >= COATS.size() || ear < 0 || ear >= EARS.size()
                || tail < 0 || tail >= TAILS.size())
            throw new IllegalArgumentException("Unknown Kavat trait");
    }

    public int packed() {
        return breed | coat << 8 | ear << 16 | tail << 24;
    }
    public static KavatGenes unpack(int bits) {
        return new KavatGenes(bits & 255, bits >>> 8 & 255, bits >>> 16 & 255, bits >>> 24);
    }
    public String model() {
        return BREEDS.get(breed) + "_" + EARS.get(ear) + "_" + TAILS.get(tail);
    }

    public static KavatGenes random(IntUnaryOperator pick) {
        return new KavatGenes(pick.applyAsInt(BREEDS.size()), pick.applyAsInt(COATS.size()),
                pick.applyAsInt(EARS.size()), pick.applyAsInt(TAILS.size()));
    }

    public static KavatGenes codes(List<KavatGenes> samples, IntUnaryOperator pick) {
        if (samples.size() != 10)
            throw new IllegalArgumentException("Ten genetic codes required");
        if (pick.applyAsInt(2) == 0)
            return random(pick);
        return new KavatGenes(samples.get(pick.applyAsInt(10)).breed, samples.get(pick.applyAsInt(10)).coat,
                samples.get(pick.applyAsInt(10)).ear, samples.get(pick.applyAsInt(10)).tail);
    }

    public record Line(KavatGenes genes, int generation) {
    }

    public static Line cross(Line a, Line b, IntUnaryOperator pick) {
        KavatGenes x = a.genes, y = b.genes;
        if (x.equals(y)) {
            int g = Math.min(100, Math.max(a.generation, b.generation));
            var child = new KavatGenes(mutate(x.breed, BREEDS.size(), g, pick), mutate(x.coat, COATS.size(), g, pick),
                    mutate(x.ear, EARS.size(), g, pick), mutate(x.tail, TAILS.size(), g, pick));
            return new Line(child, child.equals(x) ? g + 1 : 0);
        }
        return new Line(new KavatGenes(x.breed == y.breed ? x.breed : pick.applyAsInt(BREEDS.size()),
                                arc(x.coat, y.coat, COATS.size(), pick), arc(x.ear, y.ear, EARS.size(), pick),
                                arc(x.tail, y.tail, TAILS.size(), pick)),
                0);
    }
    private static int mutate(int original, int count, int generation, IntUnaryOperator pick) {
        if (pick.applyAsInt(100) >= generation)
            return original;
        return (original + 1 + pick.applyAsInt(count - 1)) % count;
    }
    private static int arc(int a, int b, int n, IntUnaryOperator pick) {
        int distance = Math.floorMod(b - a, n);
        if (distance * 2 == n)
            return pick.applyAsInt(n);
        if (distance * 2 > n)
            return (b + pick.applyAsInt(n - distance + 1)) % n;
        return (a + pick.applyAsInt(distance + 1)) % n;
    }
}

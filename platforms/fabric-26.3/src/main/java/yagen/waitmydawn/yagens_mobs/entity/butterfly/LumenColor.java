package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum LumenColor {
    GOLD("gold", Blocks.DANDELION, 0),
    SILVER("silver", Blocks.AZURE_BLUET, 1),
    RED("red", Blocks.POPPY, 2),
    BLACK("black", Blocks.WITHER_ROSE, 3),
    PINK("pink", Blocks.PINK_TULIP, 4),
    GREEN("green", Blocks.CACTUS, 5),
    BLUE("blue", Blocks.CORNFLOWER, 6),
    BROWN("brown", Blocks.BROWN_MUSHROOM, 7),
    LIGHT_BLUE("light_blue", Blocks.BLUE_ORCHID, 8);

    static final int FULL_LINEAGE = 0b111111111;
    private static final LumenColor[] COLORS = values();

    private final String id;
    private final Block plant;
    private final int lineageBit;

    LumenColor(String id, Block plant, int lineageIndex) {
        this.id = id;
        this.plant = plant;
        this.lineageBit = 1 << lineageIndex;
    }

    int lineageBit() {
        return lineageBit;
    }

    public String id() {
        return id;
    }

    Block plant() {
        return plant;
    }

    static LumenColor byId(String id) {
        for (LumenColor color : COLORS) {
            if (color.id.equals(id)) {
                return color;
            }
        }
        return GOLD;
    }

    static LumenColor random(RandomSource random) {
        return COLORS[random.nextInt(COLORS.length)];
    }
}

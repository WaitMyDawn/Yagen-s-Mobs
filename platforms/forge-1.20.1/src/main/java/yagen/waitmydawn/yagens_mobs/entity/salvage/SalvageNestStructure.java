package yagen.waitmydawn.yagens_mobs.entity.salvage;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

final class SalvageNestStructure extends Structure {
    static final Codec<SalvageNestStructure> CODEC = simpleCodec(SalvageNestStructure::new);

    private SalvageNestStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var settings = ServerConfigs.SALVAGE_RAY;
        if (!settings.canSpawn() || context.random().nextDouble() >= settings.nestChance().get()) {
            return Optional.empty();
        }
        int middleX = context.chunkPos().getMiddleBlockX();
        int middleZ = context.chunkPos().getMiddleBlockZ();
        int order = context.random().nextInt(4);
        for (int i = 0; i < 4; i++) {
            Direction front = Direction.from2DDataValue((order + i) & 3);
            Direction side = front.getClockWise();
            for (int offset = -4; offset <= 4; offset += 4) {
                int x = middleX + side.getStepX() * offset;
                int z = middleZ + side.getStepZ() * offset;
                for (int depth = -5; depth <= 5; depth++) {
                    int px = x + front.getStepX() * depth;
                    int pz = z + front.getStepZ() * depth;
                    int y = terrain(context, px, pz);
                    if (y < 41 || y > 106 || terrain(context, px + front.getStepX() * 7, pz + front.getStepZ() * 7) > y - 16
                            || terrain(context, px - front.getStepX() * 3, pz - front.getStepZ() * 3) < y - 3) {
                        continue;
                    }
                    BlockPos center = new BlockPos(px, y - 1, pz);
                    return Optional.of(new GenerationStub(center, pieces -> pieces.addPiece(new SalvageNestPiece(center, front))));
                }
            }
        }
        return Optional.empty();
    }

    private static int terrain(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    @Override
    public StructureType<?> type() {
        return SalvageRays.NEST.get();
    }
}

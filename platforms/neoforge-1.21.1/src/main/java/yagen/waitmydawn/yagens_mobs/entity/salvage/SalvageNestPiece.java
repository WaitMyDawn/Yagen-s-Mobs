package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class SalvageNestPiece extends StructurePiece {
    private static final BlockState ROCK = Blocks.END_STONE.defaultBlockState();
    private static final BlockState BRICK = Blocks.END_STONE_BRICKS.defaultBlockState();
    private static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
    private static final BlockState SLAB = Blocks.PURPUR_SLAB.defaultBlockState();
    private final BlockPos center;
    private final Direction front;

    SalvageNestPiece(BlockPos center, Direction front) {
        super(SalvageRays.NEST_PIECE.get(), 0, BoundingBox.fromCorners(center.offset(-6, -5, -6), center.offset(6, 7, 6)));
        this.center = center;
        this.front = front;
    }

    SalvageNestPiece(CompoundTag tag) {
        super(SalvageRays.NEST_PIECE.get(), tag);
        center = BlockPos.of(tag.getLong("Center"));
        front = Direction.from2DDataValue(tag.getInt("Front"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("Center", center.asLong());
        tag.putInt("Front", front.get2DDataValue());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos origin) {
        // Every intersecting chunk must see the same footprint, weathering and plant connections.
        RandomSource layout = RandomSource.create(level.getSeed() ^ center.asLong());
        int side = layout.nextBoolean() ? 1 : -1;
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        shelf(blocks, layout, side);
        rim(blocks, layout, side);
        chorus(blocks, side * 4, -2, 3 + layout.nextInt(2), front.getClockWise().getOpposite());
        chorus(blocks, -side * 3, -3, 2 + layout.nextInt(2), front);
        chorus(blocks, side * 3, 2, 2, null);
        blocks.put(at(0, 1, 0), SalvageRays.NEST_BOX.get().defaultBlockState());
        for (var entry : blocks.entrySet()) {
            if (chunkBounds.isInside(entry.getKey()) && getBoundingBox().isInside(entry.getKey())) {
                level.setBlock(entry.getKey(), entry.getValue(), 2);
            }
        }
        BlockPos boxPos = at(0, 1, 0);
        if (chunkBounds.isInside(boxPos) && level.getBlockEntity(boxPos) instanceof SalvageNestBoxEntity box) {
            box.seedWildGroup();
            level.scheduleTick(boxPos, SalvageRays.NEST_BOX.get(), 1);
        }
    }

    private void shelf(Map<BlockPos, BlockState> blocks, RandomSource layout, int side) {
        for (int v = -4; v <= 5; v++) {
            for (int u = -5; u <= 5; u++) {
                int width = v < -2 ? 4 : v <= 1 ? 5 : v <= 3 ? 4 : 2;
                int skew = v >= 2 ? side : 0;
                if (Math.abs(u - skew) > width
                        || Math.abs(u - skew) == width && layout.nextBoolean()
                        && !(u == side * 4 && v == -2) && !(u == side * 3 && v == 2)) {
                    continue;
                }
                boolean openCenter = Math.abs(u) <= 2 && v >= -2 && v <= 3;
                pos(blocks, u, 0, v, openCenter && layout.nextInt(5) == 0 ? BRICK : ROCK);
                if (openCenter) {
                    for (int y = 1; y <= 4; y++) {
                        pos(blocks, u, y, v, Blocks.AIR.defaultBlockState());
                    }
                }
                int depth = v <= 0 ? 1 + layout.nextInt(4) : layout.nextInt(3);
                for (int y = 1; y <= depth; y++) {
                    pos(blocks, u, -y, v, ROCK);
                }
            }
        }
    }

    private void rim(Map<BlockPos, BlockState> blocks, RandomSource layout, int side) {
        for (int u = -4; u <= 4; u++) {
            int height = 1 + layout.nextInt(u * side > 0 ? 4 : 2);
            for (int y = 0; y < height; y++) {
                pos(blocks, u, y, -4, y > 0 && layout.nextInt(4) == 0 ? BRICK : ROCK);
                if (y < height - 1 && layout.nextBoolean()) {
                    pos(blocks, u, y, -3, ROCK);
                }
            }
        }
        pos(blocks, -side * 3, 1, 1, SLAB);
        pos(blocks, -side * 4, 0, 0, PURPUR);
        pos(blocks, -side * 4, 1, 0, ROCK);
        pos(blocks, side * 3, 0, -1, PURPUR);
        pos(blocks, side * 3, 1, -1, PURPUR);
        pos(blocks, side * 3, 2, -1, Blocks.END_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
        pos(blocks, -side * 2, 0, 3, SLAB);
        pos(blocks, side * 2, -1, 4, BRICK);
    }

    private void chorus(Map<BlockPos, BlockState> blocks, int u, int v, int height, Direction branch) {
        BlockPos root = at(u, 0, v);
        blocks.put(root, ROCK);
        Map<BlockPos, BlockState> plant = new HashMap<>();
        BlockState stem = Blocks.CHORUS_PLANT.defaultBlockState();
        BlockState flower = Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5);
        for (int y = 1; y <= height; y++) {
            plant.put(root.above(y), stem);
        }
        plant.put(root.above(height + 1), flower);
        if (branch != null) {
            BlockPos fork = root.above(height - 1).relative(branch);
            plant.put(fork, stem);
            plant.put(fork.above(), flower);
        }
        plant.forEach((pos, state) -> {
            BlockState connected = state;
            if (state.is(Blocks.CHORUS_PLANT)) {
                for (Direction face : Direction.values()) {
                    BlockPos neighbor = pos.relative(face);
                    connected = connected.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face),
                            neighbor.equals(root) || plant.containsKey(neighbor));
                }
            }
            blocks.put(pos, connected);
        });
    }

    private void pos(Map<BlockPos, BlockState> blocks, int u, int y, int v, BlockState state) {
        blocks.put(at(u, y, v), state);
    }

    private BlockPos at(int u, int y, int v) {
        return center.relative(front.getClockWise(), u).relative(front, v).above(y);
    }
}

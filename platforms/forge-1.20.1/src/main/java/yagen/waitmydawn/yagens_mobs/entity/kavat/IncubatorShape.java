package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

final class IncubatorShape {
    static final int STEPS = 8;
    private static final VoxelShape[][] PARTS = new VoxelShape[8][STEPS + 1];

    static {
        for (int part = 0; part < 8; part++) {
            var footprint = footprint(part);
            for (int step = 0; step <= STEPS; step++) PARTS[part][step] = build(footprint, part >= 4, step);
        }
    }

    static VoxelShape part(int index, int step) { return PARTS[index][step]; }

    private record Strip(int x, int z, int endX, int endZ, int section) {}

    private static VoxelShape build(List<Strip> footprint, boolean upper, int step) {
        double travel = step / (double) STEPS;
        travel = net.minecraft.util.Mth.clamp((travel - .216) / .784, 0, 1);
        double lip = 4.15 + (16 - 4.15) * travel * travel * (3 - 2 * travel);
        VoxelShape result = Shapes.empty();
        for (var strip : footprint) {
            double height = strip.section == 1 ? (upper ? 3.2 : 2) : strip.section == 2 ? lip : 16;
            var box = Block.box(strip.x, upper ? 16 - height : 0, strip.z,
                    strip.endX, upper ? 16 : height, strip.endZ);
            result = Shapes.joinUnoptimized(result, box, BooleanOp.OR);
        }
        return result.optimize();
    }

    private static List<Strip> footprint(int part) {
        int[][] grid = new int[16][16];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            grid[z][x] = section((part & 1) * 16 + x, ((part >> 1) & 1) * 16 + z);
        List<Strip> strips = new ArrayList<>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int material = grid[z][x];
            if (material == 0) continue;
            int endX = x + 1, endZ = z + 1;
            while (endX < 16 && grid[z][endX] == material) endX++;
            rows: while (endZ < 16) {
                for (int i = x; i < endX; i++) if (grid[endZ][i] != material) break rows;
                endZ++;
            }
            strips.add(new Strip(x, z, endX, endZ, material));
            for (int i = z; i < endZ; i++) java.util.Arrays.fill(grid[i], x, endX, 0);
        }
        return strips;
    }

    // Work in model pixels so the cut corners and four diagonal piston guides match at chunk boundaries.
    private static int section(int x, int z) {
        double dx = Math.abs(x + .5 - 16), dz = Math.abs(z + .5 - 16);
        double diagonal = (dx + dz) * .70710678;
        double radius = Math.max(Math.max(dx, dz), diagonal);
        if (radius > 15.35) return 0;
        if (diagonal >= 12 && diagonal <= 14.4 && Math.abs(dx - dz) < 1.7) return 3;
        return radius >= 12.9 ? 2 : 1;
    }

    private IncubatorShape() {}
}

package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

final class VoidCloudDecay {
    static final IntegerProperty STAGE = IntegerProperty.create("decay_stage", 0, 2);
    private static final int[] STAGE_TICKS = {70, 18, 12};

    private VoidCloudDecay() {}

    static void schedulePlacement(BlockState state, Level level, BlockPos pos, BlockState previous) {
        if (level instanceof ServerLevel server && !previous.is(state.getBlock())) {
            server.scheduleTick(pos, state.getBlock(), STAGE_TICKS[state.getValue(STAGE)]);
        }
    }

    static void advance(BlockState state, ServerLevel level, BlockPos pos) {
        int stage = state.getValue(STAGE);
        if (stage == STAGE_TICKS.length - 1) {
            level.removeBlock(pos, false);
            return;
        }

        int nextStage = stage + 1;
        // A saved block state and its scheduled tick resume the same stage after chunk reload.
        level.setBlock(pos, state.setValue(STAGE, nextStage), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, state.getBlock(), STAGE_TICKS[nextStage]);
    }
}

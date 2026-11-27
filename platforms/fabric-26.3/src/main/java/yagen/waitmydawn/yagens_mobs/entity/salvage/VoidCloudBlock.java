package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class VoidCloudBlock extends TransparentBlock {
    public VoidCloudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VoidCloudDecay.STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VoidCloudDecay.STAGE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        VoidCloudDecay.schedulePlacement(state, level, pos, previous);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VoidCloudDecay.advance(state, level, pos);
    }
}

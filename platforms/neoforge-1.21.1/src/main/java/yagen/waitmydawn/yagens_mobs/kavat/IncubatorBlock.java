package yagen.waitmydawn.yagens_mobs.kavat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class IncubatorBlock extends BaseEntityBlock {
    private static final MapCodec<IncubatorBlock> CODEC = simpleCodec(IncubatorBlock::new);
    public IncubatorBlock(Properties properties) {
        super(properties);
    }
    @Override
    protected MapCodec<IncubatorBlock> codec() {
        return CODEC;
    }
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Incubator(pos, state);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.INCUBATOR_ENTITY.get(), Incubator::tick);
    }
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof Incubator inc)
            p.openMenu(new SimpleMenuProvider((id, inv, viewer) -> new IncubatorMenu(id, inv, inc), getName()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof Incubator inc)
            inc.interrupt(server);
        super.onRemove(state, level, pos, next, moved);
    }
}

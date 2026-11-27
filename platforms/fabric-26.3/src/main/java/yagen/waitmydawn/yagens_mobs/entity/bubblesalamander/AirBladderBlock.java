package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class AirBladderBlock extends TransparentBlock {
    public static final EnumProperty<BubbleSalamanderColor> COLOR =
            EnumProperty.create("color", BubbleSalamanderColor.class);

    public AirBladderBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(COLOR, BubbleSalamanderColor.GREEN);
        for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(property -> builder.add(property));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return BladderFluid.MEDIUM.defaultFluidState();
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        var state = defaultBlockState();
        for (var side : net.minecraft.core.Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),
                    context.getLevel().getBlockState(context.getClickedPos().relative(side)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                     net.minecraft.core.Direction side, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), neighbor.is(this));
    }

    public static ItemStack stack(BubbleSalamanderColor color) {
        ItemStack stack = new ItemStack(ModItems.AIR_BLADDER.get());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, color));
        return stack;
    }

    public static BubbleSalamanderColor color(ItemStack stack) {
        var color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return color == null ? BubbleSalamanderColor.GREEN : color;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return stack(state.getValue(COLOR));
    }
}

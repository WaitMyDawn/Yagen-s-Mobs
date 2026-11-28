package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class AirBladderBlock extends TransparentBlock {
    private static final MapCodec<AirBladderBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            net.minecraft.util.StringRepresentable.fromEnum(BubbleSalamanderColor::values)
                    .fieldOf("color").forGetter(block -> block.coat)
    ).apply(instance, AirBladderBlock::new));
    public static final EnumProperty<BubbleSalamanderColor> COLOR =
            EnumProperty.create("color", BubbleSalamanderColor.class);

    private final BubbleSalamanderColor coat;

    public AirBladderBlock(Properties properties, BubbleSalamanderColor coat) {
        super(properties);
        this.coat = coat;
        var state = defaultBlockState();
        if (state.hasProperty(COLOR)) state = state.setValue(COLOR, coat);
        for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(property -> builder.add(property));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return BladderFluid.MEDIUM.defaultFluidState();
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        var state = defaultBlockState();
        for (var side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),
                    connects(context.getLevel().getBlockState(context.getClickedPos().relative(side))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                     Direction side, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(neighbor));
    }

    public static boolean connects(BlockState state) {
        return state.getBlock() instanceof AirBladderBlock;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState neighbor, Direction side) {
        return connects(neighbor) || super.skipRendering(state, neighbor, side);
    }

    public static BubbleSalamanderColor color(BlockState state) {
        return state.hasProperty(COLOR) ? state.getValue(COLOR) : ((AirBladderBlock) state.getBlock()).coat;
    }

    public static ItemStack stack(BubbleSalamanderColor color) {
        return new ItemStack(ModItems.AIR_BLADDERS.get(color).get());
    }

    public static BubbleSalamanderColor color(ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                && item.getBlock() instanceof AirBladderBlock bladder && !(bladder instanceof Legacy)) {
            return bladder.coat;
        }
        var color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return color == null ? BubbleSalamanderColor.GREEN : color;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return stack(color(state));
    }

    // The old ID retains its state property so existing chunk palettes can load losslessly.
    public static final class Legacy extends AirBladderBlock {
        private static final MapCodec<Legacy> CODEC = simpleCodec(Legacy::new);

        public Legacy(Properties properties) {
            super(properties, BubbleSalamanderColor.GREEN);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(COLOR);
        }

        @Override
        protected MapCodec<? extends TransparentBlock> codec() {
            return CODEC;
        }
    }
}

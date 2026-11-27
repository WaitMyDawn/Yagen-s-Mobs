package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import com.mojang.serialization.MapCodec;
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
    private static final MapCodec<AirBladderBlock> CODEC = simpleCodec(AirBladderBlock::new);
    public static final EnumProperty<BubbleSalamanderColor> COLOR =
            EnumProperty.create("color", BubbleSalamanderColor.class);

    public AirBladderBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(COLOR, BubbleSalamanderColor.GREEN);
        for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(property -> builder.add(property));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return BladderFluid.MEDIUM.get().defaultFluidState();
    }

    public static ItemStack stack(BubbleSalamanderColor color) {
        ItemStack stack = ModItems.AIR_BLADDER.toStack();
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, color));
        return stack;
    }

    public static BubbleSalamanderColor color(ItemStack stack) {
        var color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return color == null ? BubbleSalamanderColor.GREEN : color;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return stack(state.getValue(COLOR));
    }
}

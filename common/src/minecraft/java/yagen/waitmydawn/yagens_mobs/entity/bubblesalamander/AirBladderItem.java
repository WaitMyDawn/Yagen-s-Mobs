package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class AirBladderItem extends BlockItem {
    public AirBladderItem(AirBladderBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        if (!(getBlock() instanceof AirBladderBlock.Legacy)) return super.getPlacementState(context);
        // Old stacks still carry their coat as block state data. Place the equivalent fixed-color block.
        var color = AirBladderBlock.color(context.getItemInHand());
        var state = ModBlocks.AIR_BLADDERS.get(color).get().getStateForPlacement(context);
        return state != null && canPlace(context, state) ? state : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!(getBlock() instanceof AirBladderBlock.Legacy)) return super.getName(stack);
        return Component.translatable("block.yagens_mobs." + AirBladderVariants.path(AirBladderBlock.color(stack)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return AirBladderBlock.color(stack) == BubbleSalamanderColor.MIXED || super.isFoil(stack);
    }
}

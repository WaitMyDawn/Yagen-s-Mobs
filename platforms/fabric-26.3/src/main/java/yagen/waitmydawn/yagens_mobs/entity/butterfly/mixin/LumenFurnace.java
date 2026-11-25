package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenWork;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class LumenFurnace implements LumenWork.Progress {
    @Shadow
    int litTimeRemaining;
    @Shadow
    protected NonNullList<ItemStack> items;
    @Shadow
    @Final
    private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    @Shadow
    protected abstract int getBurnDuration(ServerLevel level, ItemStack fuel);

    @Shadow
    private static boolean canBurn(NonNullList<ItemStack> slots, int maxStackSize, ItemStack result) {
        throw new AssertionError();
    }

    @Override
    public boolean lumen$isWorking() {
        var furnace = (AbstractFurnaceBlockEntity) (Object) this;
        if (!(furnace.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack input = items.getFirst();
        if (input.isEmpty() || litTimeRemaining <= 1 && getBurnDuration(level, items.get(1)) <= 0) {
            return false;
        }
        // A completed recipe resets progress to zero even if the next smelt can start immediately.
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        var recipe = quickCheck.getRecipeFor(recipeInput, level).orElse(null);
        return recipe != null && canBurn(items, furnace.getMaxStackSize(), recipe.value().assemble(recipeInput));
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void lumen$advance(ServerLevel level, BlockPos pos, BlockState state,
                                      AbstractFurnaceBlockEntity furnace, CallbackInfo callback) {
        LumenWork.advance(level, pos, (LumenWork.Progress) (Object) furnace,
                () -> AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace));
    }
}

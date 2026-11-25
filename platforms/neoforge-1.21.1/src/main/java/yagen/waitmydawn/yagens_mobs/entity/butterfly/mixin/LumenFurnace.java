package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
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
    int litTime;
    @Shadow
    protected NonNullList<ItemStack> items;
    @Shadow
    @Final
    private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    @Shadow
    protected abstract int getBurnDuration(ItemStack fuel);

    @Shadow
    private static boolean canBurn(RegistryAccess registries, RecipeHolder<?> recipe,
                                   NonNullList<ItemStack> slots, int maxStackSize, AbstractFurnaceBlockEntity furnace) {
        throw new AssertionError();
    }

    @Override
    public boolean lumen$isWorking() {
        var furnace = (AbstractFurnaceBlockEntity) (Object) this;
        ItemStack input = items.getFirst();
        if (input.isEmpty() || litTime <= 1 && getBurnDuration(items.get(1)) <= 0) {
            return false;
        }
        Level level = furnace.getLevel();
        // A completed recipe resets progress to zero even if the next smelt can start immediately.
        RecipeHolder<?> recipe = quickCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
        return canBurn(level.registryAccess(), recipe, items, furnace.getMaxStackSize(), furnace);
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void lumen$advance(Level level, BlockPos pos, BlockState state,
                                      AbstractFurnaceBlockEntity furnace, CallbackInfo callback) {
        if (level instanceof ServerLevel server) {
            LumenWork.advance(server, pos, (LumenWork.Progress) (Object) furnace,
                    () -> AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace));
        }
    }
}

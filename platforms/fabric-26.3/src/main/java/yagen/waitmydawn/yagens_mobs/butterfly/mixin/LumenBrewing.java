package yagen.waitmydawn.yagens_mobs.butterfly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenWork;

@Mixin(BrewingStandBlockEntity.class)
abstract class LumenBrewing implements LumenWork.Progress {
    @Shadow
    int brewTime;

    @Override
    public boolean lumen$isWorking() {
        return brewTime > 0;
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void lumen$advance(ServerLevel level, BlockPos pos, BlockState state,
                                      BrewingStandBlockEntity stand, CallbackInfo callback) {
        LumenWork.advance(level, pos, (LumenWork.Progress) (Object) stand,
                () -> BrewingStandBlockEntity.serverTick(level, pos, level.getBlockState(pos), stand));
    }
}

package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenWork;

@Mixin(BrewingStandBlockEntity.class)
abstract class LumenBrewing implements LumenWork.Progress {
    @Shadow
    int brewTime;

    @Override
    public boolean lumen$isWorking() {
        return brewTime > 0;
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void lumen$advance(Level level, BlockPos pos, BlockState state,
                                      BrewingStandBlockEntity stand, CallbackInfo callback) {
        if (level instanceof ServerLevel server) {
            LumenWork.advance(server, pos, (LumenWork.Progress) (Object) stand,
                    () -> BrewingStandBlockEntity.serverTick(level, pos, level.getBlockState(pos), stand));
        }
    }
}

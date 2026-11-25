package yagen.waitmydawn.yagens_mobs.entity.butterfly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherRoseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterfly;

@Mixin(WitherRoseBlock.class)
abstract class LumenWitherRose {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void lumen$immunity(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean precise, CallbackInfo callback) {
        if (entity instanceof LumenButterfly) {
            callback.cancel();
        }
    }
}

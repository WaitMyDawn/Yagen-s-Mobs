package yagen.waitmydawn.yagens_mobs.entity.weasel.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.weasel.WeaselChestRevisions;

@Mixin(BlockEntity.class)
abstract class WeaselChestChanges {
    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void yagensMobs$trackChestChanges(CallbackInfo callback) {
        if ((Object) this instanceof ChestBlockEntity chest && chest.getLevel() instanceof ServerLevel level) {
            WeaselChestRevisions.mark(level, chest.getBlockPos());
        }
    }
}


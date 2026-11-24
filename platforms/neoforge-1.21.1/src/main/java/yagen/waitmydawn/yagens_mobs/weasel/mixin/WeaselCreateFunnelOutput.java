package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.weasel.NeoCreateStorage;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity", remap = false)
abstract class WeaselCreateFunnelOutput {
    @WrapOperation(method = "activateExtractor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), require = 0)
    private boolean yagensMobs$rememberOutput(Level level, Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(level, entity);
        if (spawned) NeoCreateStorage.rememberOutput((BlockEntity) (Object) this, entity);
        return spawned;
    }
}

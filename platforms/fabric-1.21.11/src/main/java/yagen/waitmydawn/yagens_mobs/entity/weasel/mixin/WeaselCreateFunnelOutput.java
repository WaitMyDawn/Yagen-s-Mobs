package yagen.waitmydawn.yagens_mobs.entity.weasel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.yagens_mobs.entity.weasel.FabricCreateStorage;

@Pseudo
@Mixin(targets = "com.zurrtum.create.content.logistics.funnel.FunnelBlockEntity", remap = false)
abstract class WeaselCreateFunnelOutput {
    @WrapOperation(method = "activateExtractor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = false), require = 0)
    private boolean yagensMobs$rememberOutput(Level level, Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(level, entity);
        if (spawned) FabricCreateStorage.rememberOutput((BlockEntity) (Object) this, entity);
        return spawned;
    }
    @WrapOperation(method = "activateExtractor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_1937;method_8649(Lnet/minecraft/class_1297;)Z", remap = false), require = 0)
    private boolean yagensMobs$rememberIntermediaryOutput(Level level, Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(level, entity);
        if (spawned) FabricCreateStorage.rememberOutput((BlockEntity) (Object) this, entity);
        return spawned;
    }
}

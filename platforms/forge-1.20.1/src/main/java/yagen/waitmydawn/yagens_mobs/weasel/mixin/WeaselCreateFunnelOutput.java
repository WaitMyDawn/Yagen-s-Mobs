package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import yagen.waitmydawn.yagens_mobs.weasel.ForgeCreateStorage;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity", remap = false)
abstract class WeaselCreateFunnelOutput {
    @ModifyArg(method = "activateExtractor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), index = 0, require = 0)
    private Entity yagensMobs$rememberNamedOutput(Entity entity) {
        ForgeCreateStorage.rememberOutput((BlockEntity) (Object) this, entity);
        return entity;
    }

    @ModifyArg(method = "activateExtractor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"), index = 0, require = 0)
    private Entity yagensMobs$rememberRuntimeOutput(Entity entity) {
        ForgeCreateStorage.rememberOutput((BlockEntity) (Object) this, entity);
        return entity;
    }
}

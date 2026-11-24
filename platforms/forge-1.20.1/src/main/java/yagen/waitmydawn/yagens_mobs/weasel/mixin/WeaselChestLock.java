package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import net.minecraft.world.LockCode;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BaseContainerBlockEntity.class)
public interface WeaselChestLock {
    @Accessor("lockKey")
    LockCode yagensMobs$lock();
}


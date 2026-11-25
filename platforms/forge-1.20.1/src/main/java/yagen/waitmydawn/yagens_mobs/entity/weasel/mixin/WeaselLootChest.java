package yagen.waitmydawn.yagens_mobs.entity.weasel.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomizableContainerBlockEntity.class)
public interface WeaselLootChest {
    @Accessor("lootTable")
    @Nullable ResourceLocation yagensMobs$lootTable();
}

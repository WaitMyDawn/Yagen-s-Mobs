package yagen.waitmydawn.yagens_mobs.butterfly.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExperienceOrb.class)
public interface LumenOrbAccess {
    @Accessor("count")
    int lumen$count();

    @Accessor("count")
    void lumen$setCount(int count);
}

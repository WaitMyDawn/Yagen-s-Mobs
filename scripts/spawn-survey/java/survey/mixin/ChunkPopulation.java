package survey.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import survey.Census;

@Mixin(WorldGenRegion.class)
abstract class ChunkPopulation {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void recordPopulation(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && entity instanceof Mob mob && mob.getSpawnType() == MobSpawnType.CHUNK_GENERATION)
            Census.population((WorldGenRegion) (Object) this, mob);
    }
}

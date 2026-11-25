package survey.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import survey.Census;

@Mixin(NaturalSpawner.class)
abstract class SpawnDraw {
    @Inject(method = "spawnForChunk", at = @At("HEAD"))
    private static void eligibleChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
            boolean friendlies, boolean monsters, boolean animals, CallbackInfo callback) {
        if (Census.diagnostic) Census.eligibleChunk(level);
    }

    @Inject(method = "isValidSpawnPostitionForType", at = @At("RETURN"))
    private static void candidate(ServerLevel level, MobCategory category, StructureManager structures,
            ChunkGenerator generator, MobSpawnSettings.SpawnerData data, BlockPos.MutableBlockPos pos, double distance,
            CallbackInfoReturnable<Boolean> result) {
        if (Census.diagnostic && Census.observing(level))
            Census.count(result.getReturnValue() ? "candidate_pass" : "candidate_fail", level, pos, data.type);
    }
    @Inject(method = "getRandomSpawnMobAt", at = @At("RETURN"))
    private static void recordDraw(ServerLevel level, StructureManager structures, ChunkGenerator generator,
            MobCategory category, RandomSource rng, BlockPos pos,
            CallbackInfoReturnable<Optional<MobSpawnSettings.SpawnerData>> result) {
        if (Census.observing(level))
            result.getReturnValue().ifPresent(entry -> Census.count("draw", level, pos, entry.type));
    }
}

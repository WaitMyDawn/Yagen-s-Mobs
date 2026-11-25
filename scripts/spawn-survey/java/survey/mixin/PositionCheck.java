package survey.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import survey.Census;

@Mixin(value = EventHooks.class, remap = false)
abstract class PositionCheck {
    @Inject(method = "checkSpawnPosition", at = @At("RETURN"))
    private static void position(
            Mob mob, ServerLevelAccessor world, MobSpawnType reason, CallbackInfoReturnable<Boolean> result) {
        if (Census.diagnostic && reason == MobSpawnType.NATURAL && world instanceof ServerLevel level
                && Census.observing(level))
            Census.count(result.getReturnValue() ? "position_pass" : "position_fail", level, mob.blockPosition(),
                    mob.getType());
    }
}

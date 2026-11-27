package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;

public final class BladderView {
    private BladderView() {}

    public static boolean eyesInsideMembrane(LivingEntity viewer) {
        return viewer.level().getFluidState(BlockPos.containing(viewer.getEyePosition())).getType() instanceof BladderFluid;
    }
}

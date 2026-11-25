package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PetHazards {
    private PetHazards() {}

    public static boolean safe(Level level, AABB box, boolean plantImmune) {
        // Include contact with the supporting block (magma and campfires damage entities on top).
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX + .001); x <= Mth.floor(box.maxX - .001); x++) {
            for (int z = Mth.floor(box.minZ + .001); z <= Mth.floor(box.maxZ - .001); z++) {
                cursor.set(x, Mth.floor(box.minY), z);
                if (!level.hasChunkAt(cursor)) return false;
                for (int y = Mth.floor(box.minY - .08); y <= Mth.floor(box.maxY - .001); y++) {
                    cursor.set(x, y, z);
                    var state = level.getBlockState(cursor);
                    if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)
                            || state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state)
                            || state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) > 0
                            || !plantImmune && (state.is(Blocks.CACTUS) || state.is(Blocks.WITHER_ROSE))) return false;
                }
            }
        }
        return true;
    }

    public static Vec3 steer(Mob mob, Vec3 motion, boolean flies, boolean plantImmune) {
        var level = mob.level();
        var box = mob.getBoundingBox();
        Vec3 lookAhead = motion.lengthSqr() > 1.e-6 ? motion.normalize().scale(.28) : Vec3.ZERO;
        if (safe(level, box.move(motion.add(lookAhead)), plantImmune)) return motion;

        // Deflect autonomous motion before contact. Do not carry a grounded animal over a ledge.
        double yaw = motion.horizontalDistanceSqr() > .002 ? Math.atan2(motion.z, motion.x)
                : Math.toRadians(mob.getYRot() + 90);
        boolean escaping = !safe(level, box, plantImmune);
        for (int i = 0; i < 8; i++) {
            int offset = ((i + 1) / 2) * ((i & 1) == 1 ? 1 : -1);
            double angle = yaw + offset * Math.PI / 4;
            Vec3 step = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            var ahead = box.move(step.scale(.85));
            if (!safe(level, ahead, plantImmune) || !level.noCollision(mob, ahead)) continue;
            if (!flies && mob.onGround()) {
                var support = BlockPos.containing(mob.position().add(step.scale(.85))).below();
                if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) continue;
            }
            var escape = step.scale(Math.max(.12, Math.min(.32, motion.horizontalDistance())));
            escape = escape.add(0, flies ? Math.max(.04, motion.y) : Math.max(0, motion.y), 0);
            if (escaping || safe(level, box.move(escape), plantImmune)) return escape;
        }
        if (flies && level.noCollision(mob, box.move(0, .23, 0))) return new Vec3(0, .18, 0);
        if (!escaping) return Vec3.ZERO;

        // In a wide hazard every immediately adjacent footprint may still be dangerous.
        // Walk toward a safe edge when visible, otherwise keep making horizontal progress.
        Vec3 fallback = null;
        for (int i = 0; i < 8; i++) {
            double angle = yaw + i * Math.PI / 4;
            var direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            var step = direction.scale(.18);
            if (!level.noCollision(mob, box.move(step))) continue;
            var support = BlockPos.containing(mob.position().add(direction)).below();
            if (!level.hasChunkAt(support) || mob.onGround()
                    && !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) continue;
            var escape = step.add(0, mob.isInLava() ? Math.max(.12, motion.y) : motion.y, 0);
            if (fallback == null) fallback = escape;
            if (safe(level, box.move(direction.scale(2.5)), plantImmune)) return escape;
        }
        return fallback == null ? motion : fallback;
    }
}

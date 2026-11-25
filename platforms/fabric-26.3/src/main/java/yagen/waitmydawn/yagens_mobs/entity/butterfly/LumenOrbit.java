package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class LumenOrbit {
    private final Vec3 majorAxis;
    private final Vec3 minorAxis;
    private final double startingPhase;
    private final int period;

    LumenOrbit(UUID butterfly) {
        RandomSource random = RandomSource.create(butterfly.getMostSignificantBits() ^ butterfly.getLeastSignificantBits());
        double yaw = random.nextDouble() * Math.PI * 2;
        double tilt = (random.nextDouble() - .5) * 1.20;
        double major = 1.2 + random.nextDouble() * .6;
        double minor = major * (.69 + random.nextDouble() * .31);
        majorAxis = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(major);
        minorAxis = new Vec3(-Math.sin(yaw) * Math.cos(tilt), Math.sin(tilt), Math.cos(yaw) * Math.cos(tilt)).scale(minor);
        startingPhase = random.nextDouble() * Math.PI * 2;
        period = 155 + random.nextInt(85);
    }

    Vec3 position(ServerPlayer owner, long gameTime) {
        double angle = startingPhase + Math.floorMod(gameTime, period) * (Math.PI * 2 / period);
        return owner.position().add(0, owner.getEyeHeight() - .12, 0)
                .add(majorAxis.scale(Math.cos(angle))).add(minorAxis.scale(Math.sin(angle)));
    }

    static Vec3 nearOwner(ServerLevel level, ServerPlayer owner, LumenButterfly butterfly, boolean water) {
        Vec3 center = owner.position();
        if (!butterfly.isBaby() || water) {
            Vec3 orbit = new LumenOrbit(butterfly.getUUID()).position(owner, level.getGameTime());
            if (safe(level, butterfly, orbit, water)) {
                return orbit;
            }
        }
        for (int height = 0; height <= 8; height++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Vec3 point = center.add(x * .7, height, z * .7);
                    if (safe(level, butterfly, point, water)) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    static boolean safe(ServerLevel level, LumenButterfly butterfly, Vec3 target, boolean water) {
        BlockPos pos = BlockPos.containing(target);
        var box = butterfly.getDimensions(butterfly.getPose()).makeBoundingBox(target);
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos)
                && yagen.waitmydawn.yagens_mobs.entity.PetHazards.safe(level, box, true)
                && level.noCollision(butterfly.level() == level ? butterfly : null, box)
                && (level.getFluidState(pos).isEmpty() || water && level.getFluidState(pos).is(FluidTags.WATER));
    }
}

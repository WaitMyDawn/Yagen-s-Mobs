package yagen.waitmydawn.yagens_mobs.entity.butterfly.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenExperienceParticle;

final class LumenSpark extends TextureSheetParticle {
    private final Vec3 origin;
    private final int targetId;
    private final int delay;
    private final double arc;

    LumenSpark(ClientLevel level, LumenExperienceParticle option, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        origin = new Vec3(x, y, z);
        targetId = option.target();
        delay = random.nextInt(11);
        Entity target = level.getEntity(targetId);
        lifetime = delay + (target == null ? 18 : Mth.clamp((int) (12 + target.distanceToSqr(x, y, z) * .12), 14, 28));
        arc = .35 + random.nextDouble() * .38;
        hasPhysics = false;
        quadSize = .10F + random.nextFloat() * .04F;
        alpha = delay == 0 ? 1 : 0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        Entity target = level.getEntity(targetId);
        if (age++ >= lifetime || target == null || target.isRemoved()) {
            remove();
            return;
        }
        if (age <= delay) {
            return;
        }
        double t = (double) (age - delay) / (lifetime - delay);
        double height = Math.sin(Math.PI * t) * arc;
        Vec3 destination = target.getPosition(1).add(0, target.getEyeHeight(), 0);
        Vec3 point = origin.lerp(destination, t).add(0, height, 0);
        setPos(point.x, point.y, point.z);
        alpha = (float) Math.min(1, (1 - t) * 8);
        oRoll = roll;
        roll += .14F;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    static Particle create(LumenExperienceParticle option, ClientLevel level, double x, double y, double z,
                           double dx, double dy, double dz, SpriteSet sprites) {
        return new LumenSpark(level, option, x, y, z, sprites);
    }
}

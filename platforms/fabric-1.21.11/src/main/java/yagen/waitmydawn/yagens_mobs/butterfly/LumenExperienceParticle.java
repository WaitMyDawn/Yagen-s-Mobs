package yagen.waitmydawn.yagens_mobs.butterfly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record LumenExperienceParticle(int target) implements ParticleOptions {
    static final MapCodec<LumenExperienceParticle> CODEC = Codec.INT.fieldOf("target")
            .xmap(LumenExperienceParticle::new, LumenExperienceParticle::target);

    static final StreamCodec<RegistryFriendlyByteBuf, LumenExperienceParticle> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(LumenExperienceParticle::new, LumenExperienceParticle::target).cast();

    @Override
    public ParticleType<LumenExperienceParticle> getType() {
        return LumenButterflies.EXPERIENCE.get();
    }

    static void flyTo(ServerLevel level, Vec3 from, Entity target, int count) {
        level.sendParticles(new LumenExperienceParticle(target.getId()),
                from.x, from.y, from.z, count, .07, .06, .07, 0);
    }
}

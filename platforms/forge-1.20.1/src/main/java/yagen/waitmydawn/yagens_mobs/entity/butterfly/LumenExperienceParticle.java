package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record LumenExperienceParticle(int target) implements ParticleOptions {
    static final Codec<LumenExperienceParticle> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.INT.fieldOf("target").forGetter(LumenExperienceParticle::target))
                    .apply(instance, LumenExperienceParticle::new));

    static final ParticleOptions.Deserializer<LumenExperienceParticle> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public LumenExperienceParticle fromCommand(ParticleType<LumenExperienceParticle> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            return new LumenExperienceParticle(reader.readInt());
        }

        @Override
        public LumenExperienceParticle fromNetwork(ParticleType<LumenExperienceParticle> type, FriendlyByteBuf buffer) {
            return new LumenExperienceParticle(buffer.readVarInt());
        }
    };

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeVarInt(target);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + target;
    }

    @Override
    public ParticleType<LumenExperienceParticle> getType() {
        return LumenButterflies.EXPERIENCE.get();
    }

    static void flyTo(ServerLevel level, Vec3 from, Entity target, int count) {
        level.sendParticles(new LumenExperienceParticle(target.getId()),
                from.x, from.y, from.z, count, .07, .06, .07, 0);
    }
}

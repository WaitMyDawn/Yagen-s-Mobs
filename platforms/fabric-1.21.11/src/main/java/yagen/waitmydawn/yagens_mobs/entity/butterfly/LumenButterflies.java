package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class LumenButterflies {
    public static final String NAME = "lumen_butterfly";
    private static final Identifier ID = Identifier.fromNamespaceAndPath(ModIdentity.ID, NAME);
    private static final EntityType<LumenButterfly> ENTITY_TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, ID,
            EntityType.Builder.of(LumenButterfly::new, MobCategory.CREATURE).sized(.28F, .265F).eyeHeight(.12F)
                    .clientTrackingRange(10).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE, ID)));
    public static final Supplier<EntityType<LumenButterfly>> TYPE = () -> ENTITY_TYPE;
    private static final ParticleType<LumenExperienceParticle> PARTICLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "lumen_experience"), new ParticleType<>(false) {
                @Override
                public MapCodec<LumenExperienceParticle> codec() {
                    return LumenExperienceParticle.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, LumenExperienceParticle> streamCodec() {
                    return LumenExperienceParticle.STREAM_CODEC;
                }
            });
    public static final Supplier<ParticleType<LumenExperienceParticle>> EXPERIENCE = () -> PARTICLE;

    static final TicketType RECALL_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "lumen_butterfly_recall"),
            new TicketType(60, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private LumenButterflies() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(ENTITY_TYPE, LumenButterfly.attributes());
        SpawnPlacements.register(ENTITY_TYPE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LumenButterfly::checkSpawnPlacement);
        ServerTickEvents.END_WORLD_TICK.register(level ->
                level.players().forEach(LumenSwarm::playerTick));
    }

    static void clearTotemEffects(LivingEntity entity) {
        entity.removeAllEffects();
    }
}

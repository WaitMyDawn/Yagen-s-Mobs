package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class LumenButterflies {
    public static final String NAME = "lumen_butterfly";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModIdentity.ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ModIdentity.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LumenButterfly>> TYPE = ENTITIES.register(NAME,
            id -> EntityType.Builder.of(LumenButterfly::new, MobCategory.CREATURE).sized(.28F, .265F).eyeHeight(.12F)
                    .clientTrackingRange(10).updateInterval(2).build(id.toString()));
    public static final DeferredItem<DeferredSpawnEggItem> EGG = ITEMS.register(NAME + "_spawn_egg",
            () -> new DeferredSpawnEggItem(TYPE, 0x80542F, 0xEBDD76, new Item.Properties()));
    public static final DeferredHolder<ParticleType<?>, ParticleType<LumenExperienceParticle>> EXPERIENCE =
            PARTICLES.register("lumen_experience", () -> new ParticleType<>(false) {
                @Override
                public MapCodec<LumenExperienceParticle> codec() {
                    return LumenExperienceParticle.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, LumenExperienceParticle> streamCodec() {
                    return LumenExperienceParticle.STREAM_CODEC;
                }
            });

    private LumenButterflies() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        PARTICLES.register(bus);
        bus.addListener(LumenButterflies::attributes);
        bus.addListener(LumenButterflies::placement);
        bus.addListener(LumenButterflies::creativeTab);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer owner) {
                LumenSwarm.playerTick(owner);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer owner) {
                LumenSwarm.changeDimension(owner);
            }
        });
    }

    static void clearTotemEffects(LivingEntity entity) {
        entity.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TYPE.get(), LumenButterfly.attributes().build());
    }

    private static void placement(RegisterSpawnPlacementsEvent event) {
        event.register(TYPE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LumenButterfly::checkSpawnPlacement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.YAGENS_MOBS.getKey())) {
            event.accept(EGG.get());
        }
    }
}


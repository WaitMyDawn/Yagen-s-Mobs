package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class LumenButterflies {
    public static final String NAME = "lumen_butterfly";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModIdentity.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModIdentity.ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModIdentity.ID);

    public static final RegistryObject<EntityType<LumenButterfly>> TYPE = ENTITIES.register(NAME,
            () -> EntityType.Builder.of(LumenButterfly::new, MobCategory.CREATURE).sized(.28F, .265F)
                    .clientTrackingRange(10).updateInterval(2).build(ModIdentity.ID + ":" + NAME));
    public static final RegistryObject<ForgeSpawnEggItem> EGG = ITEMS.register(NAME + "_spawn_egg",
            () -> new ForgeSpawnEggItem(TYPE, 0x80542F, 0xEBDD76, new Item.Properties()));
    public static final RegistryObject<ParticleType<LumenExperienceParticle>> EXPERIENCE =
            PARTICLES.register("lumen_experience", () -> new ParticleType<LumenExperienceParticle>(false, LumenExperienceParticle.DESERIALIZER) {
                @Override
                public Codec<LumenExperienceParticle> codec() {
                    return LumenExperienceParticle.CODEC;
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
        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer owner) {
                LumenSwarm.playerTick(owner);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer owner) {
                LumenSwarm.changeDimension(owner);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent event) ->
                event.setAmount(LumenSwarm.shield(event.getEntity(), event.getSource(), event.getAmount())));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingExperienceDropEvent event) -> {
            if (event.getEntity().level() instanceof ServerLevel level) {
                event.setDroppedExperience(LumenSwarm.routeExperience(level, event.getEntity(),
                        event.getAttackingPlayer(), event.getDroppedExperience()));
            }
        });
    }

    static void clearTotemEffects(LivingEntity entity) {
        entity.removeAllEffects();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TYPE.get(), LumenButterfly.attributes().build());
    }

    private static void placement(SpawnPlacementRegisterEvent event) {
        event.register(TYPE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LumenButterfly::checkSpawnPlacement, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.YAGENS_MOBS.getKey())) {
            event.accept(EGG.get());
        }
    }
}


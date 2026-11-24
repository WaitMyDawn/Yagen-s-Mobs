package yagen.waitmydawn.yagens_mobs.weasel;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SnowcacheWeasels {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModIdentity.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<SnowcacheWeasel>> TYPE = ENTITIES.register("snowcache_weasel",
            id -> EntityType.Builder.of(SnowcacheWeasel::new, MobCategory.CREATURE).sized(.44F, .60F).eyeHeight(.38F)
                    .clientTrackingRange(10).updateInterval(2).build(id.toString()));
    private static final DeferredItem<DeferredSpawnEggItem> EGG = ITEMS.register("snowcache_weasel_spawn_egg",
            () -> new DeferredSpawnEggItem(TYPE, 0xBACED7, 0x536B92, new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<RecipeBlueprint> BLUEPRINT = ITEMS.register("recipe_blueprint", RecipeBlueprint::new);

    private SnowcacheWeasels() {}

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
    }

    static boolean blueprintsEnabled() {
        return ModList.get().isLoaded("jei");
    }

    static boolean allowTame(SnowcacheWeasel weasel, net.minecraft.world.entity.player.Player player) {
        return !net.neoforged.neoforge.event.EventHooks.onAnimalTame(weasel, player);
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(TYPE.get(), SnowcacheWeasel.attributes().build()));
        bus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(TYPE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SnowcacheWeasel::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        bus.addListener(SnowcacheWeasels::creative);
        bus.addListener(WeaselPackets::register);
        NeoForge.EVENT_BUS.addListener(SnowcacheWeasels::configureChest);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            for (var passenger : event.getEntity().getPassengers()) {
                if (passenger instanceof SnowcacheWeasel weasel) {
                    weasel.leaveShoulder(event.getEntity(), false);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                WeaselTabs.invalidate();
                BlueprintRecipes.invalidate();
            }
        });
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ModItems.YAGENS_MOBS.getKey())) {
            return;
        }
        if (blueprintsEnabled()) {
            event.insertBefore(new ItemStack(ModItems.DESERT_SKATE_SPAWN_EGG.get()), BLUEPRINT.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        event.accept(EGG.get());
    }

    private static void configureChest(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        for (var passenger : player.getPassengers()) {
            if (!(passenger instanceof SnowcacheWeasel weasel) || !weasel.isOwnedBy(player)) {
                continue;
            }
            if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                if (player instanceof ServerPlayer server) {
                    var chest = WeaselChests.at(server.serverLevel(), event.getPos(), server);
                    if (chest != null) {
                        weasel.cycleChest(chest.pos());
                    } else {
                        weasel.tell("unavailable");
                    }
                }
            } else if (event.getItemStack().isEmpty()) {
                if (!event.getLevel().isClientSide) {
                    weasel.leaveShoulder(player, true);
                }
            } else {
                return;
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }
    }
}


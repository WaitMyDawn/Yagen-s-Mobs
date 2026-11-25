package yagen.waitmydawn.yagens_mobs.entity.weasel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SnowcacheWeasels {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, ModIdentity.ID);
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, ModIdentity.ID);
    public static final RegistryObject<EntityType<SnowcacheWeasel>> TYPE = ENTITIES.register("snowcache_weasel",
            () -> EntityType.Builder.of(SnowcacheWeasel::new, MobCategory.CREATURE).sized(.44F, .60F)
                    .clientTrackingRange(10).updateInterval(2).build(id("snowcache_weasel").toString()));
    private static final RegistryObject<ForgeSpawnEggItem> EGG = ITEMS.register("snowcache_weasel_spawn_egg",
            () -> new ForgeSpawnEggItem(TYPE, 0xBACED7, 0x536B92, new net.minecraft.world.item.Item.Properties()));
    public static final RegistryObject<RecipeBlueprint> BLUEPRINT = ITEMS.register("recipe_blueprint", RecipeBlueprint::new);

    private SnowcacheWeasels() {}

    public static ResourceLocation id(String name) {
        return new ResourceLocation(ModIdentity.ID, name);
    }

    static boolean blueprintsEnabled() {
        return ModList.get().isLoaded("jei");
    }

    static boolean allowTame(SnowcacheWeasel weasel, net.minecraft.world.entity.player.Player player) {
        return !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(weasel, player);
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(TYPE.get(), SnowcacheWeasel.attributes().build()));
        bus.addListener((SpawnPlacementRegisterEvent event) -> event.register(TYPE.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SnowcacheWeasel::checkSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE));
        bus.addListener(SnowcacheWeasels::creative);
        MinecraftForge.EVENT_BUS.addListener(SnowcacheWeasels::configureChest);
        MinecraftForge.EVENT_BUS.addListener(WeaselPortPackets::onOpen);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            for (var passenger : event.getEntity().getPassengers()) {
                if (passenger instanceof SnowcacheWeasel weasel) {
                    weasel.leaveShoulder(event.getEntity(), false);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
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
            event.getEntries().putBefore(ModItems.DESERT_SKATE_SPAWN_EGG.get().getDefaultInstance(), BLUEPRINT.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        event.accept(EGG.get());
    }

    private static void configureChest(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isSpectator()) return;
        var pos = event.getPos();
        var level = event.getLevel();
        var device = level.getBlockEntity(pos);
        for (var passenger : player.getPassengers()) {
            if (!(passenger instanceof SnowcacheWeasel weasel) || !weasel.isOwnedBy(player)) continue;
            if (WeaselPorts.isRefined(device)) {
                if (player instanceof ServerPlayer server) WeaselPortPackets.nativeInteraction(server, weasel, device);
                return;
            }
            if (!player.isShiftKeyDown()) return;
            if (WeaselPorts.isCreate(device)) {
                if (!event.getItemStack().isEmpty() || !WeaselPorts.isVaultPort(level, device)) return;
                if (player instanceof ServerPlayer server) WeaselPortPackets.createInteraction(server, weasel, device);
            } else if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    || WeaselPorts.physical(device)) {
                if (player instanceof ServerPlayer server) {
                    var chest = WeaselChests.at(server.serverLevel(), pos, server);
                    if (chest != null) weasel.cycleStorage(chest);
                    else weasel.tell("unavailable");
                }
            } else if (event.getItemStack().isEmpty()) {
                if (!level.isClientSide) weasel.leaveShoulder(player, true);
            } else {
                return;
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
            return;
        }
    }
}

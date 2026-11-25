package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SnowcacheWeasels {
    private static final EntityType<SnowcacheWeasel> ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("snowcache_weasel"), EntityType.Builder.of(SnowcacheWeasel::new, MobCategory.CREATURE)
                    .sized(.44F, .60F).eyeHeight(.38F).clientTrackingRange(10).updateInterval(2)
                    .build(id("snowcache_weasel").toString()));
    public static final Supplier<EntityType<SnowcacheWeasel>> TYPE = () -> ENTITY;
    private static RecipeBlueprint blueprint;
    public static final Supplier<RecipeBlueprint> BLUEPRINT = () -> blueprint;

    private SnowcacheWeasels() {}

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
    }

    static boolean blueprintsEnabled() {
        return FabricLoader.getInstance().isModLoaded("jei");
    }

    static boolean allowTame(SnowcacheWeasel weasel, Player player) {
        return true;
    }

    public static void register() {
        blueprint = blueprintsEnabled()
                ? ModItems.item("recipe_blueprint", RecipeBlueprint::new).get()
                : Registry.register(BuiltInRegistries.ITEM, id("recipe_blueprint"), new RecipeBlueprint());
        ModItems.item("snowcache_weasel_spawn_egg", () -> new SpawnEggItem(ENTITY, 0xBACED7, 0x536B92,
                new Item.Properties()));
        FabricDefaultAttributeRegistry.register(ENTITY, SnowcacheWeasel.attributes());
        SpawnPlacements.register(ENTITY, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SnowcacheWeasel::checkSpawn);
        WeaselPackets.register();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            for (var passenger : player.getPassengers()) {
                if (!(passenger instanceof SnowcacheWeasel weasel) || !weasel.isOwnedBy(player)) continue;
                var pos = hit.getBlockPos();
                var device = level.getBlockEntity(pos);
                if (WeaselPorts.isRefined(device)) {
                    if (player instanceof ServerPlayer server) WeaselPortPackets.nativeInteraction(server, weasel, device);
                    return InteractionResult.PASS;
                }
                if (!player.isShiftKeyDown()) return InteractionResult.PASS;
                if (WeaselPorts.isCreate(device)) {
                    if (!player.getMainHandItem().isEmpty() || !WeaselPorts.isVaultPort(level, device)) return InteractionResult.PASS;
                    if (player instanceof ServerPlayer server) WeaselPortPackets.createInteraction(server, weasel, device);
                } else if (level.getBlockState(pos).getBlock() instanceof ChestBlock || WeaselPorts.physical(device)) {
                    if (player instanceof ServerPlayer server) {
                        var chest = WeaselChests.at(server.serverLevel(), pos, server);
                        if (chest == null) weasel.tell("unavailable");
                        else weasel.cycleStorage(chest);
                    }
                } else if (player.getMainHandItem().isEmpty()) {
                    if (!level.isClientSide) weasel.leaveShoulder(player, true);
                } else {
                    return InteractionResult.PASS;
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (var passenger : handler.player.getPassengers()) {
                if (passenger instanceof SnowcacheWeasel weasel) weasel.leaveShoulder(handler.player, false);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) {
                WeaselTabs.invalidate();
                BlueprintRecipes.invalidate();
            }
        });
    }
}

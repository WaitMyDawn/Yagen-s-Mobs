package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.jetbrains.annotations.Nullable;

public final class WeaselPortPackets {
    private record Pending(BlockEntity device, SnowcacheWeasel weasel, long until) {}
    private record Editing(UUID id, BlockEntity device, SnowcacheWeasel weasel, WeaselChests.Kind kind,
                           @Nullable AbstractContainerMenu nativeMenu) {}

    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Editing> EDITING = new WeakHashMap<>();

    private WeaselPortPackets() {}

    public static boolean isNativeMenu(AbstractContainerMenu menu) {
        return WeaselPorts.REFINED && (FabricRefinedStorage.matchesMenu(WeaselChests.Kind.GRID, menu)
                || FabricRefinedStorage.matchesMenu(WeaselChests.Kind.INLET, menu)
                || FabricRefinedStorage.matchesMenu(WeaselChests.Kind.OUTLET, menu));
    }

    static void nativeInteraction(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity entity) {
        if (WeaselPorts.REFINED && FabricRefinedStorage.supports(entity)) {
            PENDING.put(player, new Pending(entity, weasel, player.level().getGameTime() + 2));
        } else {
            PENDING.remove(player);
        }
    }

    public static void onOpen(ServerPlayer player) {
        var pending = PENDING.remove(player);
        if (pending == null || player.level().getGameTime() > pending.until()) return;
        var container = player.containerMenu;
        var kind = FabricRefinedStorage.kind(pending.device());
        if (kind != null && FabricRefinedStorage.matchesMenu(kind, container)) {
            start(player, pending.weasel(), pending.device(), kind, container);
        }
    }

    static void createInteraction(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity device) {
        var site = WeaselChests.at(player.serverLevel(), device.getBlockPos(), player);
        if (site != null) start(player, weasel, device, site.endpoint().kind(), null);
    }

    private static void start(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity device,
                              WeaselChests.Kind kind, @Nullable AbstractContainerMenu nativeMenu) {
        var session = new Editing(UUID.randomUUID(), device, weasel, kind, nativeMenu);
        if (!valid(player, session)) return;
        EDITING.put(player, session);
        send(player, session);
    }

    private static boolean valid(ServerPlayer player, Editing session) {
        var level = player.serverLevel();
        var device = session.device();
        var weasel = session.weasel();
        BlockPos pos = device.getBlockPos();
        if (!player.isAlive() || player.isSpectator() || !weasel.isAlive()
                || !weasel.isOwnedBy(player) || weasel.getVehicle() != player || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !player.canInteractWithBlock(pos, 1)
                || level.getBlockEntity(pos) != device || WeaselPorts.kind(device) != session.kind()) return false;
        var menu = session.nativeMenu();
        return menu == null ? player.containerMenu == player.inventoryMenu && WeaselChests.at(level, pos, player) != null
                : player.containerMenu == menu && menu.stillValid(player) && FabricRefinedStorage.canConfigure(device, player);
    }

    private static void send(ServerPlayer player, Editing session) {
        var tag = new CompoundTag();
        tag.putBoolean("Storage", true);
        tag.putUUID("Session", session.id());
        tag.putInt("Weasel", session.weasel().getId());
        var menu = session.nativeMenu();
        tag.putInt("Menu", menu == null ? -1 : menu.containerId);
        tag.putBoolean("Grid", session.kind() == WeaselChests.Kind.GRID);
        if (session.kind() == WeaselChests.Kind.GRID) {
            var site = WeaselChests.at(player.serverLevel(), session.device().getBlockPos(), player);
            if (site == null) return;
            tag.putString("Mode", site.mode(session.weasel()).name().toLowerCase(java.util.Locale.ROOT));
        } else {
            int fallback = FabricRefinedStorage.PRIORITY;
            tag.putInt("Priority", session.weasel().storagePriority(session.device().getBlockPos(), fallback));
            tag.putString("Mode", session.kind() == WeaselChests.Kind.INLET ? "deposit" : "take");
        }
        ServerPlayNetworking.send(player, new WeaselPackets.Opening(tag));
    }

    static void update(ServerPlayer player, CompoundTag tag) {
        var session = EDITING.get(player);
        if (session == null || !tag.hasUUID("Session") || !session.id().equals(tag.getUUID("Session")) || !valid(player, session)) return;
        var weasel = session.weasel();
        var pos = session.device().getBlockPos();
        if (session.kind() == WeaselChests.Kind.GRID) {
            var site = WeaselChests.at(player.serverLevel(), pos, player);
            if (site == null) return;
            weasel.cycleStorage(site);
        } else {
            if (!tag.contains("Priority", Tag.TAG_INT)) return;
            int priority = tag.getInt("Priority");
            if (priority < 0 || priority > 999) return;
            weasel.setStoragePriority(pos, priority, FabricRefinedStorage.PRIORITY);
        }
        send(player, session);
    }

}

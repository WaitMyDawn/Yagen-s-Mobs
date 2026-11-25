package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class WeaselPortPackets {
    private record Editing(UUID id, BlockEntity device, SnowcacheWeasel weasel, WeaselChests.Kind kind) {}
    private static final Map<ServerPlayer, Editing> EDITING = new WeakHashMap<>();

    private WeaselPortPackets() {}

    static void nativeInteraction(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity device) {}

    static void createInteraction(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity device) {
        var site = WeaselChests.at(player.level(), device.getBlockPos(), player);
        if (site != null) start(player, weasel, device, site.endpoint().kind());
    }

    private static void start(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity device,
                              WeaselChests.Kind kind) {
        var session = new Editing(UUID.randomUUID(), device, weasel, kind);
        if (!valid(player, session)) return;
        EDITING.put(player, session);
        send(player, session);
    }

    private static boolean valid(ServerPlayer player, Editing session) {
        var level = player.level();
        var device = session.device();
        var weasel = session.weasel();
        BlockPos pos = device.getBlockPos();
        if (!player.isAlive() || player.isSpectator() || !weasel.isAlive()
                || !weasel.isOwnedBy(player) || weasel.getVehicle() != player || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !player.isWithinBlockInteractionRange(pos, 1)
                || level.getBlockEntity(pos) != device || WeaselPorts.kind(device) != session.kind()) return false;
        return player.containerMenu == player.inventoryMenu && WeaselChests.at(level, pos, player) != null;
    }

    private static void send(ServerPlayer player, Editing session) {
        var tag = new CompoundTag();
        tag.putBoolean("Storage", true);
        tag.putString("Session", session.id().toString());
        tag.putInt("Weasel", session.weasel().getId());
        tag.putInt("Priority", session.weasel().storagePriority(session.device().getBlockPos(), FabricCreateStorage.PRIORITY));
        tag.putString("Mode", session.kind() == WeaselChests.Kind.INLET ? "deposit" : "take");
        ServerPlayNetworking.send(player, new WeaselPackets.Opening(tag));
    }

    static void update(ServerPlayer player, CompoundTag tag) {
        var session = EDITING.get(player);
        if (session == null || !session.id().toString().equals(tag.getStringOr("Session", "")) || !valid(player, session)) return;
        int priority = tag.getIntOr("Priority", -1);
        if (priority < 0 || priority > 999) return;
        session.weasel().setStoragePriority(session.device().getBlockPos(), priority, FabricCreateStorage.PRIORITY);
        send(player, session);
    }
}

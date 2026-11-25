package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.client.WeaselPortScreen;

public final class WeaselPortPackets {
    private record Pending(BlockEntity device, SnowcacheWeasel weasel, long until) {}
    private record Editing(UUID id, BlockEntity device, SnowcacheWeasel weasel, WeaselChests.Kind kind,
                           @Nullable AbstractContainerMenu nativeMenu) {}

    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Editing> EDITING = new WeakHashMap<>();

    private WeaselPortPackets() {}

    public static boolean isNativeMenu(AbstractContainerMenu menu) {
        return WeaselPorts.REFINED && (NeoRefinedStorage.matchesMenu(WeaselChests.Kind.GRID, menu)
                || NeoRefinedStorage.matchesMenu(WeaselChests.Kind.INLET, menu)
                || NeoRefinedStorage.matchesMenu(WeaselChests.Kind.OUTLET, menu));
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Update.TYPE, Update.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) update(player, payload.tag());
        });
        registrar.playToClient(Opening.TYPE, Opening.CODEC, (payload, context) -> WeaselPortScreen.open(payload.tag()));
    }

    static void nativeInteraction(ServerPlayer player, SnowcacheWeasel weasel, BlockEntity entity) {
        if (WeaselPorts.REFINED && NeoRefinedStorage.supports(entity)) {
            PENDING.put(player, new Pending(entity, weasel, player.level().getGameTime() + 2));
        } else {
            PENDING.remove(player);
        }
    }

    static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var pending = PENDING.remove(player);
        if (pending == null || player.level().getGameTime() > pending.until()) return;
        var container = event.getContainer();
        var kind = NeoRefinedStorage.kind(pending.device());
        if (kind != null && NeoRefinedStorage.matchesMenu(kind, container)) {
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
                : player.containerMenu == menu && menu.stillValid(player) && NeoRefinedStorage.canConfigure(device, player);
    }

    private static void send(ServerPlayer player, Editing session) {
        var tag = new CompoundTag();
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
            int fallback = menu == null ? NeoCreateStorage.PRIORITY : NeoRefinedStorage.PRIORITY;
            tag.putInt("Priority", session.weasel().storagePriority(session.device().getBlockPos(), fallback));
            tag.putString("Mode", session.kind() == WeaselChests.Kind.INLET ? "deposit" : "take");
        }
        PacketDistributor.sendToPlayer(player, new Opening(tag));
    }

    private static void update(ServerPlayer player, CompoundTag tag) {
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
            weasel.setStoragePriority(pos, priority, session.nativeMenu() == null ? NeoCreateStorage.PRIORITY : NeoRefinedStorage.PRIORITY);
        }
        send(player, session);
    }

    public record Update(CompoundTag tag) implements CustomPacketPayload {
        static final Type<Update> TYPE = new Type<>(SnowcacheWeasels.id("weasel_port_update"));
        static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Update::tag, Update::new);
        @Override public Type<Update> type() { return TYPE; }
    }

    public record Opening(CompoundTag tag) implements CustomPacketPayload {
        static final Type<Opening> TYPE = new Type<>(SnowcacheWeasels.id("weasel_port_open"));
        static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Opening::tag, Opening::new);
        @Override public Type<Opening> type() { return TYPE; }
    }
}

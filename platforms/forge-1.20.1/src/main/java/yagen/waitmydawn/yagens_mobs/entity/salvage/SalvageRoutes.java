package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.core.SegmentApproach;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkPath;

public final class SalvageRoutes {
    private static final int PAGE_SIZE = 6;
    private static final double LINE_REACH = .18;
    private static final Map<ServerPlayer, Long> NEXT_OVERLAY = new WeakHashMap<>();

    private SalvageRoutes() {}

    static CompoundTag action(String op) {
        CompoundTag data = new CompoundTag();
        data.putString("Action", op);
        return data;
    }

    public static void handle(Request request, ServerPlayer player) {
        if (player == null || player.isSpectator() || request.data() == null) {
            return;
        }
        CompoundTag data = request.data();
        var level = player.serverLevel();
        var directory = SalvageNestDirectory.of(level);
        switch (data.getString("Action")) {
            case "Overlay" -> {
                long now = level.getGameTime();
                if (!hasCompass(player) || now < NEXT_OVERLAY.getOrDefault(player, 0L)) {
                    return;
                }
                NEXT_OVERLAY.put(player, now + 15);
                sendOverlay(player);
            }
            case "CutLine" -> {
                if (!hasCompass(player) || !player.isShiftKeyDown()) {
                    return;
                }
                var link = SalvageNestDirectory.Link.read(data.getCompound("Link"));
                Vec3 eye = player.getEyePosition();
                Vec3 view = player.getViewVector(1).scale(player.getBlockReach());
                double reach = RockSilkPath.visibleReach(player, eye, eye.add(view));
                if (link != null && aimAt(link, eye, view.normalize().scale(reach)) && directory.disconnect(link, player)) {
                    SalvageNestBox.message(player, "link_removed");
                    sendOverlay(player);
                } else {
                    SalvageNestBox.message(player, "link_unavailable");
                }
            }
            case "Manage" -> {
                if (!hasCompass(player)) {
                    return;
                }
                BlockPos pos = BlockPos.of(data.getLong("Origin"));
                var box = SalvageNestDirectory.loaded(level, pos);
                if (box == null || !data.hasUUID("OriginId") || !box.id().equals(data.getUUID("OriginId"))
                        || !player.canReach(pos, 1) || !level.mayInteract(player, pos)) {
                    return;
                }
                if (data.hasUUID("TargetId")) {
                    var target = new SalvageNestDirectory.End(BlockPos.of(data.getLong("Target")), data.getUUID("TargetId"));
                    if (directory.disconnect(SalvageNestDirectory.Link.between(box.endpoint(), target), player)) {
                        SalvageNestBox.message(player, "link_removed");
                        sendOverlay(player);
                    } else {
                        SalvageNestBox.message(player, "link_unavailable");
                    }
                }
                manage(player, box, data.getInt("Page"), false);
            }
            case "Rename" -> {
                if (player.containerMenu instanceof SalvageNestMenu menu && menu.containerId == data.getInt("Menu")
                        && menu.stillValid(player)) {
                    var box = SalvageNestDirectory.loaded(level, menu.box.pos());
                    if (box != null && box.id().equals(menu.box.id()) && box.mayConfigure(player) && level.mayInteract(player, menu.box.pos())) {
                        String name = net.minecraft.SharedConstants.filterText(data.getString("Name")).strip();
                        if (name.length() <= 64) {
                            box.nameChanged(name.isEmpty() ? null : Component.literal(name));
                            var reply = new CompoundTag();
                            reply.putString("Mode", "Rename");
                            reply.putString("Name", name);
                            reply.putInt("Menu", menu.containerId);
                            LegacyNetwork.sendToPlayer(player, new Snapshot(reply));
                        }
                    }
                }
            }
            default -> {
                if (!(level.getEntity(data.getInt("Ray")) instanceof SalvageRay ray) || !ray.canOffer(player)) {
                    return;
                }
                if (data.contains("Target")) {
                    if (!ray.beginRide(player, BlockPos.of(data.getLong("Target")))) {
                        player.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.route_failed"), true);
                    }
                } else {
                    sendRide(player, ray, data.getInt("Page"), false);
                }
            }
        }
    }

    static boolean hasCompass(Player player) { return player.isHolding(Items.COMPASS); }

    static double pickFraction(SalvageNestDirectory.Link link, Vec3 eye, Vec3 view) {
        Vec3 from = link.a().marker();
        var near = SegmentApproach.between(eye, view, from, link.b().marker().subtract(from));
        return near.distanceSqr() <= LINE_REACH * LINE_REACH ? near.firstFraction() : Double.POSITIVE_INFINITY;
    }

    private static boolean aimAt(SalvageNestDirectory.Link link, Vec3 eye, Vec3 view) {
        return view.lengthSqr() > .01 && pickFraction(link, eye, view) < 1;
    }

    private static void sendOverlay(ServerPlayer player) {
        var data = new CompoundTag();
        data.putString("Mode", "Overlay");
        data.putString("Dimension", player.level().dimension().location().toString());
        var rows = new ListTag();
        int chunks = Math.min(SalvagePlayerMotion.viewDistance(player), player.server.getPlayerList().getViewDistance());
        SalvageNestDirectory.of(player.serverLevel()).visible(player.position(), net.minecraft.util.Mth.clamp(chunks, 2, 32) * 16)
                .forEach(link -> rows.add(link.save()));
        data.put("Links", rows);
        LegacyNetwork.sendToPlayer(player, new Snapshot(data));
    }

    static void onCompass(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getItemStack().is(Items.COMPASS) && player.isShiftKeyDown() && !player.isSpectator()
                && event.getLevel().getBlockState(event.getPos()).is(SalvageRays.NEST_BOX.get())) {
            if (player instanceof ServerPlayer server && server.canReach(event.getPos(), 1)
                    && event.getLevel().getBlockEntity(event.getPos()) instanceof SalvageNestBoxEntity box) {
                manage(server, box, 0, true);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    private static void manage(ServerPlayer player, SalvageNestBoxEntity box, int page, boolean open) {
        var view = new CompoundTag();
        view.putString("Mode", "Manage");
        view.putLong("Origin", box.getBlockPos().asLong());
        view.putUUID("OriginId", box.id());
        var directory = SalvageNestDirectory.of(player.serverLevel());
        send(player, view, directory.neighbors(box.endpoint()), page, open, box.mayConfigure(player));
    }

    static void open(ServerPlayer player, SalvageRay ray) {
        sendRide(player, ray, 0, true);
    }

    private static void sendRide(ServerPlayer player, SalvageRay ray, int page, boolean open) {
        var view = new CompoundTag();
        view.putInt("Ray", ray.getId());
        send(player, view, ray.routes(player.serverLevel()), page, open, true);
    }

    private static void send(ServerPlayer player, CompoundTag view, List<SalvageNestDirectory.Site> destinations,
                             int page, boolean open, boolean editable) {
        destinations = destinations.stream().sorted(Comparator.comparingDouble(site -> player.distanceToSqr(Vec3.atCenterOf(site.pos())))).toList();
        int pages = Math.max(1, (destinations.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Mth.clamp(page, 0, pages - 1);
        view.putInt("Page", page);
        view.putInt("Pages", pages);
        view.putBoolean("Open", open);
        ListTag rows = new ListTag();
        for (var site : destinations.subList(page * PAGE_SIZE, Math.min(destinations.size(), (page + 1) * PAGE_SIZE))) {
            CompoundTag row = new CompoundTag();
            Vec3 delta = Vec3.atCenterOf(site.pos()).subtract(player.position());
            row.putInt("Distance", (int) Math.round(delta.length()));
            row.putInt("Bearing", Math.floorMod((int) Math.round(Math.atan2(delta.x, -delta.z) / (Math.PI / 4)), 8));
            row.putString("Name", site.name());
            row.putLong("Pos", site.pos().asLong());
            row.putUUID("Id", site.id());
            row.putBoolean("Editable", editable || site.mayConfigure(player));
            rows.add(row);
        }
        view.put("Rows", rows);
        LegacyNetwork.sendToPlayer(player, new Snapshot(view));
    }

    public record Request(CompoundTag data) {}

    public record Snapshot(CompoundTag data) {}
}

package yagen.waitmydawn.yagens_mobs.entity.salvage;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.core.SegmentApproach;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkPath;

public final class SalvageRoutes {
    private static final int PAGE_SIZE = 6;
    private static final double LINE_REACH = .18;
    private static final Map<ServerPlayer, Long> NEXT_OVERLAY = new WeakHashMap<>();

    private SalvageRoutes() {}

    static void register() {
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE, Snapshot.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) -> {
            if (!context.player().isSpectator()) {
                handle(context.player(), request.data());
            }
        });
    }

    static CompoundTag action(String op) {
        CompoundTag data = new CompoundTag();
        data.putString("Action", op);
        return data;
    }

    private static void handle(ServerPlayer player, CompoundTag data) {
        var level = player.level();
        var directory = SalvageNestDirectory.of(level);
        switch (data.getStringOr("Action", "")) {
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
                var link = SalvageNestDirectory.Link.read(data.getCompoundOrEmpty("Link"));
                Vec3 eye = player.getEyePosition();
                Vec3 view = player.getViewVector(1).scale(player.blockInteractionRange());
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
                BlockPos pos = BlockPos.of(data.getLongOr("Origin", 0L));
                var box = SalvageNestDirectory.loaded(level, pos);
                if (box == null || !SaveTag.id(data, "OriginId").isPresent() || !box.id().equals(SaveTag.id(data, "OriginId").orElseThrow())
                        || !player.isWithinBlockInteractionRange(pos, 1) || !level.mayInteract(player, pos)) {
                    return;
                }
                if (SaveTag.id(data, "TargetId").isPresent()) {
                    var target = new SalvageNestDirectory.End(BlockPos.of(data.getLongOr("Target", 0L)), SaveTag.id(data, "TargetId").orElseThrow());
                    if (directory.disconnect(SalvageNestDirectory.Link.between(box.endpoint(), target), player)) {
                        SalvageNestBox.message(player, "link_removed");
                        sendOverlay(player);
                    } else {
                        SalvageNestBox.message(player, "link_unavailable");
                    }
                }
                manage(player, box, data.getIntOr("Page", 0), false);
            }
            case "Rename" -> {
                if (player.containerMenu instanceof SalvageNestMenu menu && menu.containerId == data.getIntOr("Menu", 0)
                        && menu.stillValid(player)) {
                    var box = SalvageNestDirectory.loaded(level, menu.box.pos());
                    if (box != null && box.id().equals(menu.box.id()) && box.mayConfigure(player) && level.mayInteract(player, menu.box.pos())) {
                        String name = net.minecraft.util.StringUtil.filterText(data.getStringOr("Name", ""), false).strip();
                        if (name.length() <= 64) {
                            box.nameChanged(name.isEmpty() ? null : Component.literal(name));
                            var reply = new CompoundTag();
                            reply.putString("Mode", "Rename");
                            reply.putString("Name", name);
                            reply.putInt("Menu", menu.containerId);
                            ServerPlayNetworking.send(player, new Snapshot(reply));
                        }
                    }
                }
            }
            default -> {
                if (!(level.getEntity(data.getIntOr("Ray", 0)) instanceof SalvageRay ray) || !ray.canOffer(player)) {
                    return;
                }
                if (data.contains("Target")) {
                    if (!ray.beginRide(player, BlockPos.of(data.getLongOr("Target", 0L)))) {
                        player.displayClientMessage(Component.translatable("message.yagens_mobs.salvage_ray.route_failed"), true);
                    }
                } else {
                    sendRide(player, ray, data.getIntOr("Page", 0), false);
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
        data.putString("Dimension", player.level().dimension().identifier().toString());
        var rows = new ListTag();
        int chunks = Math.min(player.requestedViewDistance(), player.level().getServer().getPlayerList().getViewDistance());
        SalvageNestDirectory.of(player.level()).visible(player.position(), Math.clamp(chunks, 2, 32) * 16)
                .forEach(link -> rows.add(link.save()));
        data.put("Links", rows);
        ServerPlayNetworking.send(player, new Snapshot(data));
    }

    static InteractionResult onCompass(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!player.getItemInHand(hand).is(Items.COMPASS) || !player.isShiftKeyDown() || player.isSpectator()
                || !level.getBlockState(pos).is(SalvageRays.NEST_BOX.get())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && server.isWithinBlockInteractionRange(pos, 1)
                && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box) {
            manage(server, box, 0, true);
        }
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    private static void manage(ServerPlayer player, SalvageNestBoxEntity box, int page, boolean open) {
        var view = new CompoundTag();
        view.putString("Mode", "Manage");
        view.putLong("Origin", box.getBlockPos().asLong());
        SaveTag.putId(view, "OriginId", box.id());
        var directory = SalvageNestDirectory.of(player.level());
        send(player, view, directory.neighbors(box.endpoint()), page, open, box.mayConfigure(player));
    }

    static void open(ServerPlayer player, SalvageRay ray) {
        sendRide(player, ray, 0, true);
    }

    private static void sendRide(ServerPlayer player, SalvageRay ray, int page, boolean open) {
        var view = new CompoundTag();
        view.putInt("Ray", ray.getId());
        send(player, view, ray.routes(player.level()), page, open, true);
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
            SaveTag.putId(row, "Id", site.id());
            row.putBoolean("Editable", editable || site.mayConfigure(player));
            rows.add(row);
        }
        view.put("Rows", rows);
        ServerPlayNetworking.send(player, new Snapshot(view));
    }

    public record Request(CompoundTag data) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(SalvageRays.id("salvage_route_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Request::data, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        static final Type<Snapshot> TYPE = new Type<>(SalvageRays.id("salvage_route_snapshot"));
        static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Snapshot::data, Snapshot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

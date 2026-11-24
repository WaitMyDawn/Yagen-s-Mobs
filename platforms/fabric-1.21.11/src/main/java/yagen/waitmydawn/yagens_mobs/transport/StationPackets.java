package yagen.waitmydawn.yagens_mobs.transport;

import yagen.waitmydawn.yagens_mobs.network.FabricPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class StationPackets {
    private static final int PAGE_SIZE = 5;

    private StationPackets() {}

    public static void register() {
        FabricPayloads.toServer(Request.TYPE, Request.CODEC, StationPackets::handleRequest);
        FabricPayloads.toClientType(Snapshot.TYPE, Snapshot.CODEC);
        FabricPayloads.toClientType(MailName.TYPE, MailName.CODEC);
    }

    private static void handleRequest(Request request, ServerPlayer player) {
        var nbt = request.data();
        var level = player.level();
        BlockPos origin = BlockPos.of(nbt.getLongOr("Origin", 0L));
        if (!level.hasChunkAt(origin) || !player.isWithinBlockInteractionRange(origin, 2.0)
                || !level.getBlockState(origin).is(ModBlocks.VULTURE_POST.get())) {
            return;
        }
        var data = StationData.get(level);
        var station = data.station(origin);
        if (station == null) {
            return;
        }
        boolean owner = station.isOwnedBy(player);
        int page = nbt.getIntOr("Page", 0);
        switch (nbt.getStringOr("Action", "")) {
            case "configure" -> {
                if (owner) {
                    data.configure(station, nbt.getStringOr("Name", station.name()),
                            nbt.getBooleanOr("Public", station.publicListing()),
                            nbt.getBooleanOr("Return", station.returnBirds()));
                }
            }
            case "deploy" -> {
                BlockPos target = BlockPos.of(nbt.getLongOr("Target", 0L));
                var destination = data.station(target);
                if (owner && destination != null && data.dispatch(level, player, station, destination)) {
                    player.displayClientMessage(Component.translatable("message.yagens_mobs.station_dispatched"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.yagens_mobs.station_failed"), true);
                }
            }
            case "withdraw" -> {
                int count = Math.min(64, data.fuel(station));
                if (owner && count > 0 && data.spendFuel(station, count)) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.ROTTEN_FLESH, count));
                }
            }
            case "deposit" -> {
                if (owner) {
                    for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                        if (stack.is(Items.ROTTEN_FLESH)) {
                            stack.shrink(data.addFuel(station, stack.getCount()));
                        }
                    }
                    player.getInventory().setChanged();
                }
            }
            case "take_mail" -> {
                if (owner && data.mailCount(station) > 0) {
                    if (player.getInventory().getFreeSlot() < 0) {
                        player.displayClientMessage(Component.translatable("message.yagens_mobs.mail.inventory_full"), false);
                    } else {
                        var bag = data.takeMail(station);
                        MailBag.refreshStationName(level, bag);
                        player.getInventory().placeItemBackInInventory(bag);
                    }
                }
            }
            default -> {
            }
        }
        send(player, origin, page, false);
    }

    public static void open(ServerPlayer player, BlockPos origin) {
        send(player, origin, 0, true);
    }

    private static void send(ServerPlayer player, BlockPos origin, int page, boolean openScreen) {
        var level = player.level();
        var data = StationData.get(level);
        var source = data.station(origin);
        if (source == null) {
            return;
        }
        var destinations = data.visibleStations(player, source);
        int pages = Math.max(1, (destinations.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Mth.clamp(page, 0, pages - 1);
        int cargo = data.cargoCount(source);
        int birds = data.perchedBirds(level, source).size();
        int fuel = data.fuel(source);
        boolean owner = source.isOwnedBy(player);
        var result = new CompoundTag();
        result.putLong("Origin", origin.asLong());
        result.putBoolean("Open", openScreen);
        result.putBoolean("Owner", owner);
        result.putInt("Mail", owner ? data.mailCount(source) : 0);
        result.putString("Name", source.name());
        result.putBoolean("Public", source.publicListing());
        result.putBoolean("Return", source.returnBirds());
        result.putInt("Cargo", cargo);
        result.putInt("Birds", birds);
        result.putInt("Fuel", fuel);
        result.putInt("FuelCapacity", StationData.FUEL_CAPACITY);
        result.putInt("Page", page);
        result.putInt("Pages", pages);
        var rows = new ListTag();
        for (var other : destinations.subList(page * PAGE_SIZE, Math.min(destinations.size(), (page + 1) * PAGE_SIZE))) {
            var entry = new CompoundTag();
            int free = data.freeColumns(other).size();
            int cost = StationData.shippingCost(source, other, cargo);
            entry.putLong("Pos", other.anchor().asLong());
            entry.putString("Name", other.name());
            entry.putBoolean("Yours", other.isOwnedBy(player));
            entry.putInt("Distance", (int) Math.round(source.center().distanceTo(other.center())));
            var direction = other.center().subtract(source.center());
            entry.putInt("Direction", Math.floorMod((int) Math.round(Math.atan2(direction.x, direction.z) / (Math.PI / 4.0)), 8));
            entry.putInt("Free", free);
            entry.putInt("Total", other.columns().size());
            entry.putInt("Cost", cost);
            entry.putBoolean("Enabled", owner && cargo > 0 && birds >= cargo && free >= cargo && fuel >= cost
                    && data.canFit(source, other));
            var reservations = new ListTag();
            data.reservations(other).forEach((sender, count) -> {
                var reservation = new CompoundTag();
                reservation.store("Label", net.minecraft.network.chat.ComponentSerialization.CODEC,
                        level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), sender);
                reservation.putInt("Count", count);
                reservations.add(reservation);
            });
            entry.put("Reservations", reservations);
            rows.add(entry);
        }
        result.put("Rows", rows);
        ServerPlayNetworking.send(player, new Snapshot(result));
    }

    public record MailName(int containerId, String name) implements CustomPacketPayload {
        public static final Type<MailName> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "mail_name"));
        static final StreamCodec<RegistryFriendlyByteBuf, MailName> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MailName::containerId, ByteBufCodecs.STRING_UTF8, MailName::name, MailName::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Request(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "vulture_station_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Request::data, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.ID, "vulture_station_snapshot"));
        static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Snapshot::data, Snapshot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

package yagen.waitmydawn.yagens_mobs.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class StationPackets {
    private static final int PAGE_SIZE = 5;

    private StationPackets() {}

    public static void handleRequest(Request request, ServerPlayer player) {
        if (player == null || request.data() == null) {
            return;
        }
        var nbt = request.data();
        var level = player.serverLevel();
        BlockPos origin = BlockPos.of(nbt.getLong("Origin"));
        if (!level.hasChunkAt(origin) || !player.canReach(origin, 2.0)
                || !level.getBlockState(origin).is(ModBlocks.VULTURE_POST.get())) {
            return;
        }
        var data = StationData.get(level);
        var station = data.station(origin);
        if (station == null) {
            return;
        }
        boolean owner = station.isOwnedBy(player);
        int page = nbt.getInt("Page");
        switch (nbt.getString("Action")) {
            case "configure" -> {
                if (owner) {
                    data.configure(station, nbt.contains("Name", Tag.TAG_STRING) ? nbt.getString("Name") : station.name(),
                            nbt.contains("Public", Tag.TAG_BYTE) ? nbt.getBoolean("Public") : station.publicListing(),
                            nbt.contains("Return", Tag.TAG_BYTE) ? nbt.getBoolean("Return") : station.returnBirds());
                }
            }
            case "deploy" -> {
                BlockPos target = BlockPos.of(nbt.getLong("Target"));
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
                    for (ItemStack stack : player.getInventory().items) {
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
        var level = player.serverLevel();
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
                reservation.putString("Label", Component.Serializer.toJson(sender));
                reservation.putInt("Count", count);
                reservations.add(reservation);
            });
            entry.put("Reservations", reservations);
            rows.add(entry);
        }
        result.put("Rows", rows);
        LegacyNetwork.sendToPlayer(player, new Snapshot(result));
    }

    public record MailName(int containerId, String name) {}

    public record Request(CompoundTag data) {}

    public record Snapshot(CompoundTag data) {}
}

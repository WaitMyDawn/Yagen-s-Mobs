package yagen.waitmydawn.yagens_mobs.entity.butterfly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

final class LumenLedger extends SavedData {
    static final int MAX_FOLLOWERS = 7;
    private static final String NAME = "yagens_mobs_lumen_butterflies";
    private static final double PRECISION = 1.0e-8;

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, Double> wildFractions = new HashMap<>();

    static LumenLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(LumenLedger::load, LumenLedger::new, NAME);
    }

    private static final class Account {
        private final List<UUID> followers = new ArrayList<>(MAX_FOLLOWERS);
        private final Map<UUID, Location> locations = new HashMap<>();
        private final Map<UUID, Long> nextRecall = new HashMap<>();
        private double pending;
        private UUID lastTotem;

        private boolean empty() {
            return pending == 0 && followers.isEmpty();
        }
    }

    record Location(ResourceLocation dimension, long chunk) {
    }

    void remember(UUID owner, LumenButterfly butterfly) {
        Account account = accounts.get(owner);
        if (account == null || !account.followers.contains(butterfly.getUUID())) {
            return;
        }
        Location location = new Location(butterfly.level().dimension().location(), butterfly.chunkPosition().toLong());
        if (!location.equals(account.locations.put(butterfly.getUUID(), location))) {
            setDirty();
        }
    }

    Location location(UUID owner, UUID pet) {
        Account account = accounts.get(owner);
        return account == null ? null : account.locations.get(pet);
    }

    boolean startRecall(UUID owner, UUID pet, long time) {
        Account account = accounts.get(owner);
        if (account == null || time < account.nextRecall.getOrDefault(pet, 0L)) {
            return false;
        }
        account.nextRecall.put(pet, time + 100);
        return true;
    }

    List<UUID> followers(UUID owner) {
        Account account = accounts.get(owner);
        return account == null ? List.of() : List.copyOf(account.followers);
    }

    int slot(UUID owner, UUID pet) {
        Account account = accounts.get(owner);
        return account == null ? -1 : account.followers.indexOf(pet);
    }

    boolean enlist(UUID owner, UUID pet) {
        Account account = accounts.computeIfAbsent(owner, ignored -> new Account());
        if (account.followers.contains(pet)) {
            return true;
        }
        if (account.followers.size() >= MAX_FOLLOWERS) {
            return false;
        }
        account.followers.add(pet);
        setDirty();
        return true;
    }

    boolean useTotem(UUID owner, List<LumenButterfly> swarm, long time) {
        Account account = accounts.get(owner);
        if (account == null || swarm.isEmpty()) {
            return false;
        }
        int previous = -1;
        for (int i = 0; i < swarm.size(); i++) {
            if (swarm.get(i).getUUID().equals(account.lastTotem)) {
                previous = i;
                break;
            }
        }
        for (int step = 1; step <= swarm.size(); step++) {
            LumenButterfly butterfly = swarm.get((previous + step) % swarm.size());
            if (butterfly.consumeTotem(time)) {
                account.lastTotem = butterfly.getUUID();
                setDirty();
                return true;
            }
        }
        return false;
    }

    void dismiss(UUID owner, UUID pet) {
        Account account = accounts.get(owner);
        if (account != null && account.followers.remove(pet)) {
            account.locations.remove(pet);
            account.nextRecall.remove(pet);
            if (account.empty()) {
                accounts.remove(owner);
            }
            setDirty();
        }
    }

    void credit(ServerLevel level, UUID owner, double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        Account account = accounts.computeIfAbsent(owner, ignored -> new Account());
        account.pending += amount;
        setDirty();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.isAlive()) {
            pay(player);
        }
    }

    double takeForDebt(ServerPlayer player, double debt) {
        Account account = accounts.get(player.getUUID());
        double fromPending = account == null ? 0 : Math.min(debt, account.pending);
        if (fromPending > 0) {
            account.pending -= fromPending;
            if (account.empty()) {
                accounts.remove(player.getUUID());
            }
            setDirty();
        }

        double remaining = debt - fromPending;
        long before = experienceInBar(player);
        int requested = (int) Math.min(before, Math.ceil(remaining - PRECISION));
        if (requested <= 0) {
            return fromPending;
        }
        player.giveExperiencePoints(-requested);
        long spent = Math.max(0, before - experienceInBar(player));
        // The player can only spend whole points. Credit any overpayment to the existing fractional ledger.
        if (spent > remaining) {
            credit(player.serverLevel(), player.getUUID(), spent - remaining);
        }
        return fromPending + Math.min(remaining, spent);
    }

    private static long experienceInBar(ServerPlayer player) {
        long level = player.experienceLevel;
        long completed = level <= 16 ? level * (level + 6)
                : level <= 31 ? (level * (5 * level - 81) + 720) / 2
                : Math.round((level * (9.0 * level - 325) + 4440) / 2);
        return completed + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    void pay(ServerPlayer player) {
        Account account = accounts.get(player.getUUID());
        if (account == null || account.pending + PRECISION < 1) {
            return;
        }
        int room = Integer.MAX_VALUE - player.totalExperience;
        int payment = (int) Math.min(room, Math.floor(account.pending + PRECISION));
        if (payment <= 0) {
            return;
        }
        account.pending = Math.max(0, account.pending - payment);
        player.giveExperiencePoints(payment);
        if (account.empty()) {
            accounts.remove(player.getUUID());
        }
        setDirty();
    }

    int wildPayout(ServerLevel level, double amount) {
        String dimension = level.dimension().location().toString();
        double total = wildFractions.getOrDefault(dimension, 0.0) + amount;
        int whole = (int) Math.floor(total + PRECISION);
        double remainder = Math.max(0, total - whole);
        if (remainder > PRECISION) {
            wildFractions.put(dimension, remainder);
        } else {
            wildFractions.remove(dimension);
        }
        setDirty();
        return whole;
    }

    private static LumenLedger load(CompoundTag tag) {
        LumenLedger data = new LumenLedger();
        for (Tag row : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) row;
            if (!entry.hasUUID("Owner")) {
                continue;
            }
            Account account = new Account();
            if (entry.hasUUID("LastTotem")) {
                account.lastTotem = entry.getUUID("LastTotem");
            }
            double pending = entry.getDouble("Pending");
            account.pending = Double.isFinite(pending) ? Math.max(0, pending) : 0;
            for (Tag uuid : entry.getList("Followers", Tag.TAG_INT_ARRAY)) {
                UUID pet = NbtUtils.loadUUID(uuid);
                if (account.followers.size() < MAX_FOLLOWERS && !account.followers.contains(pet)) {
                    account.followers.add(pet);
                }
            }
            for (Tag rowLocation : entry.getList("Locations", Tag.TAG_COMPOUND)) {
                CompoundTag location = (CompoundTag) rowLocation;
                ResourceLocation dimension = ResourceLocation.tryParse(location.getString("Dimension"));
                if (dimension != null && location.hasUUID("Pet") && account.followers.contains(location.getUUID("Pet"))) {
                    account.locations.put(location.getUUID("Pet"), new Location(dimension, location.getLong("Chunk")));
                }
            }
            if (!account.empty()) {
                data.accounts.put(entry.getUUID("Owner"), account);
            }
        }
        CompoundTag fractions = tag.getCompound("WildFractions");
        for (String dimension : fractions.getAllKeys()) {
            double amount = fractions.getDouble(dimension);
            if (amount > 0 && Double.isFinite(amount)) {
                data.wildFractions.put(dimension, amount);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag owners = new ListTag();
        accounts.forEach((id, account) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", id);
            entry.putDouble("Pending", account.pending);
            if (account.lastTotem != null) {
                entry.putUUID("LastTotem", account.lastTotem);
            }
            ListTag followers = new ListTag();
            account.followers.forEach(pet -> followers.add(NbtUtils.createUUID(pet)));
            entry.put("Followers", followers);
            ListTag locations = new ListTag();
            account.locations.forEach((pet, location) -> {
                CompoundTag saved = new CompoundTag();
                saved.putUUID("Pet", pet);
                saved.putString("Dimension", location.dimension().toString());
                saved.putLong("Chunk", location.chunk());
                locations.add(saved);
            });
            entry.put("Locations", locations);
            owners.add(entry);
        });
        tag.put("Owners", owners);
        CompoundTag fractions = new CompoundTag();
        wildFractions.forEach(fractions::putDouble);
        tag.put("WildFractions", fractions);
        return tag;
    }
}

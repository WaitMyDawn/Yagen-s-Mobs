package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

/** Offline deliveries move to the owner's death-persistent player compound on login. */
public final class KavatMemories extends SavedData {
    private static final AttachmentType<CompoundTag> ARCHIVE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "kavat_memories"),
            builder -> builder.persistent(CompoundTag.CODEC).copyOnDeath());
    private final CompoundTag inbox;
    private KavatMemories() {
        this(new CompoundTag());
    }
    private KavatMemories(CompoundTag data) {
        inbox = data;
    }
    private static KavatMemories get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(KavatMemories::new, (tag, lookup) -> new KavatMemories(tag), null), "yagens_mobs_kavat_inbox");
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return inbox.copy();
    }
    static CompoundTag child(CompoundTag parent, String name) {
        if (!parent.contains(name, 10))
            parent.put(name, new CompoundTag());
        return parent.getCompound(name);
    }
    static CompoundTag memories(Player player) {
        var archive = player.getAttached(ARCHIVE);
        return archive == null ? new CompoundTag() : archive;
    }
    static void remove(Player player, String id) {
        var records = memories(player).copy();
        records.remove(id);
        player.setAttached(ARCHIVE, records.isEmpty() ? null : records);
    }
    static void store(ServerLevel level, UUID owner, UUID id, CompoundTag record) {
        var online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            var records = memories(online).copy();
            records.put(id.toString(), record);
            online.setAttached(ARCHIVE, records);
        } else {
            var data = get(level);
            child(data.inbox, owner.toString()).put(id.toString(), record);
            data.setDirty();
        }
    }
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> deliver(connection.player));
    }
    private static void deliver(ServerPlayer p) {
        var data = get(p.serverLevel());
        String key = p.getStringUUID();
        if (data.inbox.contains(key)) {
            p.setAttached(ARCHIVE, memories(p).copy().merge(data.inbox.getCompound(key)));
            data.inbox.remove(key);
            data.setDirty();
        }
    }
}

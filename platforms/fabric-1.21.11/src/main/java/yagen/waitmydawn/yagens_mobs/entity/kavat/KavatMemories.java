package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

/** Online archives travel with player data. The overworld holds deliveries for offline owners until login. */
public final class KavatMemories extends SavedData {
    private static final AttachmentType<CompoundTag> ARCHIVE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(ModIdentity.ID, "kavat_memories"),
            builder -> builder.persistent(CompoundTag.CODEC).copyOnDeath());
    private static final SavedDataType<KavatMemories> STORAGE = new SavedDataType<>(
            "yagens_mobs_kavat_inbox", KavatMemories::new, CompoundTag.CODEC.xmap(KavatMemories::new, data -> data.inbox), null);
    private final CompoundTag inbox;

    private KavatMemories() { this(new CompoundTag()); }
    private KavatMemories(CompoundTag inbox) { this.inbox = inbox; }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> deliver(connection.player));
    }

    private static KavatMemories get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(STORAGE);
    }

    static CompoundTag memories(Player owner) {
        return owner.getAttachedOrGet(ARCHIVE, CompoundTag::new);
    }

    static void remove(Player owner, String id) {
        var records = memories(owner).copy();
        records.remove(id);
        if (records.isEmpty()) owner.removeAttached(ARCHIVE);
        else owner.setAttached(ARCHIVE, records);
    }

    static void store(ServerLevel level, UUID owner, UUID id, CompoundTag record) {
        var player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            var records = memories(player).copy();
            records.put(id.toString(), record);
            player.setAttached(ARCHIVE, records);
            return;
        }
        var data = get(level);
        String key = owner.toString();
        var records = data.inbox.getCompoundOrEmpty(key);
        records.put(id.toString(), record);
        data.inbox.put(key, records);
        data.setDirty();
    }

    private static void deliver(ServerPlayer owner) {
        var data = get(owner.level());
        String key = owner.getStringUUID();
        var pending = data.inbox.getCompound(key);
        if (pending.isEmpty()) return;
        var merged = memories(owner).copy();
        for (var entry : pending.get().entrySet()) merged.put(entry.getKey(), entry.getValue());
        owner.setAttached(ARCHIVE, merged);
        data.inbox.remove(key);
        data.setDirty();
    }
}

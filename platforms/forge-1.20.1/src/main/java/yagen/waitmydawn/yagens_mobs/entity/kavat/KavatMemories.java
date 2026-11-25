package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Offline deliveries move to the owner's death-persistent player compound on login. */
public final class KavatMemories extends SavedData {
    private final CompoundTag inbox;
    private KavatMemories() {
        this(new CompoundTag());
    }
    private KavatMemories(CompoundTag data) {
        inbox = data;
    }
    private static KavatMemories get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                KavatMemories::new, KavatMemories::new, "yagens_mobs_kavat_inbox");
    }
    @Override
    public CompoundTag save(CompoundTag tag) {
        return inbox.copy();
    }
    static CompoundTag child(CompoundTag parent, String name) {
        if (!parent.contains(name, 10))
            parent.put(name, new CompoundTag());
        return parent.getCompound(name);
    }
    static CompoundTag memories(Player player) {
        return child(child(player.getPersistentData(), Player.PERSISTED_NBT_TAG), "YagensMobsKavats");
    }
    static void store(ServerLevel level, UUID owner, UUID id, CompoundTag record) {
        var online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null)
            memories(online).put(id.toString(), record);
        else {
            var data = get(level);
            child(data.inbox, owner.toString()).put(id.toString(), record);
            data.setDirty();
        }
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p))
            return;
        var data = get(p.serverLevel());
        String key = p.getStringUUID();
        if (data.inbox.contains(key)) {
            memories(p).merge(data.inbox.getCompound(key));
            data.inbox.remove(key);
            data.setDirty();
        }
    }
}

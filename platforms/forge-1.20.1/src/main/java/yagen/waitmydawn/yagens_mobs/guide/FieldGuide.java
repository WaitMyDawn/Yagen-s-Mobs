package yagen.waitmydawn.yagens_mobs.guide;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.entity.player.PlayerEvent;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class FieldGuide extends Item {
    private static final String RECEIVED_GUIDE = "yagens_mobs_received_field_guide";

    private static final class ReaderChannel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(new ResourceLocation("yagens_mobs", "field_guide"),
                () -> "2", NetworkRegistry.acceptMissingOr("2"), NetworkRegistry.acceptMissingOr("2"));
    }

    public FieldGuide() { super(new Properties().stacksTo(1)); }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("patchouli");
    }


    static net.minecraft.server.level.ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
    }

    static java.util.Set<String> noteKeys(CompoundTag notes) {
        return notes.getAllKeys();
    }

    static long recordedDay(CompoundTag notes, String key) {
        return notes.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? notes.getLong(key) : -1L;
    }

    static boolean canRead(ServerPlayer player) {
        return player.connection.connection.isMemoryConnection() || ReaderChannel.INSTANCE.isRemotePresent(player.connection.connection);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canRead(player)) return;
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(RECEIVED_GUIDE)) return;

        var guide = ModItems.FIELD_GUIDE.get();
        if (player.getInventory().countItem(guide) == 0) {
            player.getInventory().placeItemBackInInventory(guide.getDefaultInstance());
        }
        persisted.putBoolean(RECEIVED_GUIDE, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!isAvailable()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player instanceof ServerPlayer server) {
            if (!canRead(server)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            ReaderChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> server), new Open(FieldNotes.entries(server)));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private record Open(Map<String, Long> known) {}


    static CompoundTag notes(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("yagens_mobs_field_notes");
    }

    static void saveNotes(Player player, CompoundTag notes) {
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put("yagens_mobs_field_notes", notes);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static SimpleChannel channel() {
        return ReaderChannel.INSTANCE;
    }

    public static void observe(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.player instanceof ServerPlayer player)
            FieldNotes.observe(player);
    }

    public static void register() {
        FieldPhotos.register();
        ReaderChannel.INSTANCE.messageBuilder(Open.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> buf.writeMap(message.known(), (buffer, key) -> buffer.writeUtf(key, 128), (buffer, time) -> buffer.writeLong(time)))
                .decoder(buf -> new Open(buf.readMap(buffer -> buffer.readUtf(128), buffer -> buffer.readLong())))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> yagen.waitmydawn.yagens_mobs.guide.client.PatchouliReader.open(message.known())))
                .add();
    }
}

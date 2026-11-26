package yagen.waitmydawn.yagens_mobs.guide;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class FieldGuide extends Item {
    private static final String RECEIVED_GUIDE = "yagens_mobs_received_field_guide";

    public FieldGuide() { super(new Properties().stacksTo(1)); }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("patchouli");
    }

    private static final String NOTES = "yagens_mobs_field_notes";

    static CompoundTag notes(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(NOTES);
    }

    static void saveNotes(Player player, CompoundTag notes) {
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(NOTES, notes);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static boolean canRead(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, Open.TYPE.id());
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
            var entries = new java.util.HashMap<String, Long>();
            var notes = notes(player);
            for (var key : notes.getAllKeys()) if (FieldPhotos.valid(key)) {
                // Earlier notebooks stored a boolean without a discovery date.
                entries.put(key, notes.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? notes.getLong(key) : -1L);
            }
            PacketDistributor.sendToPlayer(server, new Open(entries));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    public record Open(java.util.Map<String, Long> known) implements CustomPacketPayload {
        static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("yagens_mobs","field_guide"));
        static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (b, v) -> b.writeMap(v.known(), (buf, key) -> buf.writeUtf(key, 128), (buf,t) -> buf.writeLong(t)),
                b -> new Open(b.readMap(buf -> buf.readUtf(128), buf -> buf.readLong())));
        @Override public Type<Open> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        FieldPhotos.register(event);
        event.registrar("2").optional().playToClient(Open.TYPE, Open.CODEC, (message, context) ->
                yagen.waitmydawn.yagens_mobs.guide.client.PatchouliReader.open(message.known()));
    }
}

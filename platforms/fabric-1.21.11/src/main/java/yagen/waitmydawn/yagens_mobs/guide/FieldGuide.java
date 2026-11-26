package yagen.waitmydawn.yagens_mobs.guide;

import net.minecraft.nbt.CompoundTag;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class FieldGuide extends Item {

    public FieldGuide(Properties properties) { super(properties.stacksTo(1)); }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("patchouli");
    }

    private static final AttachmentType<CompoundTag> NOTES = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("yagens_mobs", "field_notes"), b -> b.persistent(CompoundTag.CODEC).copyOnDeath());
    private static final AttachmentType<Boolean> RECEIPT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("yagens_mobs", "received_field_guide"), b -> b.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath());

    static CompoundTag notes(Player player) {
        return player.getAttachedOrElse(NOTES, new CompoundTag());
    }

    static void saveNotes(Player player, CompoundTag notes) {
        player.setAttached(NOTES, notes);
    }

    static boolean canRead(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, Open.TYPE);
    }

    public static void onLogin(ServerPlayer player) {
        if (canRead(player)) giveOnce(player);
    }

    private static void giveOnce(ServerPlayer player) {
        if (player.getAttachedOrElse(RECEIPT, false)) return;
        var guide = ModItems.FIELD_GUIDE.get();
        if (player.getInventory().countItem(guide) == 0) {
            player.getInventory().placeItemBackInInventory(guide.getDefaultInstance());
        }
        player.setAttached(RECEIPT, true);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!isAvailable()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            if (!canRead(server)) return InteractionResult.PASS;
            ServerPlayNetworking.send(server, new Open(List.copyOf(notes(player).keySet())));
        }
        return InteractionResult.SUCCESS;
    }

    public record Open(List<String> known) implements CustomPacketPayload {
        static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("yagens_mobs","field_guide"));
        static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (b, v) -> b.writeCollection(v.known(), (buf, key) -> buf.writeUtf(key, 128)),
                b -> new Open(b.readList(buf -> buf.readUtf(128))));
        @Override public Type<Open> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Open.TYPE, Open.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onLogin(handler.player));
        net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            // Initial channel negotiation can precede player registration. JOIN handles that case.
            if (channels.contains(Open.TYPE.id()) && server.getPlayerList().getPlayer(handler.player.getUUID()) == handler.player)
                giveOnce(handler.player);
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> level.players().forEach(FieldNotes::observe));
    }
}

package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class WeaselPackets {
    private record Editing(UUID session, int slot, ItemStack item) {}
    private static final Map<ServerPlayer, Editing> EDITING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> NEXT_REQUEST = new WeakHashMap<>();

    private WeaselPackets() {}

    static void register() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Opening.TYPE, Opening.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> {
            var owner = context.player();
            if (!owner.isSpectator() && owner.isAlive() && SnowcacheWeasels.blueprintsEnabled()) {
                handle(owner, payload.tag());
            }
        });
    }

    static void open(ServerPlayer player, int slot) {
        var item = slot(player, slot);
        if (!item.is(SnowcacheWeasels.BLUEPRINT.get())) return;
        var session = new Editing(UUID.randomUUID(), slot, item);
        EDITING.put(player, session);
        var tag = new CompoundTag();
        tag.putUUID("Session", session.session());
        var plan = RecipeBlueprint.read(item);
        if (plan != null) tag.put("Plan", plan.save());
        ServerPlayNetworking.send(player, new Opening(tag));
    }

    private static void handle(ServerPlayer player, CompoundTag tag) {
        long now = player.serverLevel().getGameTime();
        if (now < NEXT_REQUEST.getOrDefault(player, 0L)) return;
        NEXT_REQUEST.put(player, now + 3);
        if (tag.contains("DragSlot")) {
            int slot = tag.getInt("DragSlot");
            ItemStack item = slot(player, slot);
            ResourceLocation output = ResourceLocation.tryParse(tag.getString("Output"));
            if (output == null || !item.is(SnowcacheWeasels.BLUEPRINT.get())) return;
            var recipe = BlueprintRecipes.get(player.serverLevel()).forOutput(output).stream().findFirst().orElse(null);
            if (recipe == null) return;
            var plan = new BlueprintPlan(output, 1, Map.of("", new BlueprintPlan.Choice(output, recipe.id(), BlueprintPlan.Mode.ANY)));
            RecipeBlueprint.write(item, plan);
            player.getInventory().setChanged();
            open(player, slot);
            return;
        }
        var editing = EDITING.get(player);
        if (editing == null || !tag.hasUUID("Session") || !editing.session().equals(tag.getUUID("Session"))
                || slot(player, editing.slot()) != editing.item()) {
            return;
        }
        var plan = BlueprintPlan.load(tag.getCompound("Plan"));
        if (plan == null || !BlueprintRecipes.get(player.serverLevel()).valid(plan)) {
            player.displayClientMessage(Component.translatable("message.yagens_mobs.snowcache_weasel.invalid_blueprint"), true);
            return;
        }
        RecipeBlueprint.write(editing.item(), plan);
        player.getInventory().setChanged();
        EDITING.remove(player);
        player.displayClientMessage(Component.translatable("message.yagens_mobs.snowcache_weasel.blueprint_saved"), true);
    }

    private static ItemStack slot(ServerPlayer player, int slot) {
        return slot >= 0 && slot < player.getInventory().getContainerSize()
                ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    public record Request(CompoundTag tag) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(SnowcacheWeasels.id("weasel_blueprint_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Request::tag, Request::new);

        @Override
        public Type<Request> type() { return TYPE; }
    }

    public record Opening(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Opening> TYPE = new Type<>(SnowcacheWeasels.id("weasel_blueprint_open"));
        static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Opening::tag, Opening::new);

        @Override
        public Type<Opening> type() { return TYPE; }
    }
}


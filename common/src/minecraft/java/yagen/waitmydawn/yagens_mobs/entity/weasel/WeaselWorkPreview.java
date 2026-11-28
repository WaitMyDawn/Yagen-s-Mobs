package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

public final class WeaselWorkPreview {
    public static final String KEY = "WorkPreview";
    public static final int REFRESH_TICKS = 15;
    private static final Map<ServerPlayer, Integer> lastRequests = new WeakHashMap<>();

    private WeaselWorkPreview() {}

    public static @Nullable SnowcacheWeasel carriedAdult(Player player) {
        if (!player.isAlive() || player.isSpectator() || !player.isShiftKeyDown()) return null;
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof SnowcacheWeasel weasel && !weasel.isBaby() && weasel.isAlive()
                    && weasel.isOwnedBy(player)) return weasel;
        }
        return null;
    }

    static void respond(ServerPlayer player, ServerLevel level, LongArrayTag data, Consumer<CompoundTag> send) {
        long[] request = data.getAsLongArray();
        if (request.length != 1) return;
        SnowcacheWeasel weasel = carriedAdult(player);
        if (weasel == null) return;
        Integer last = lastRequests.get(player);
        if (last != null && player.tickCount - last < REFRESH_TICKS / 2) return;
        lastRequests.put(player, player.tickCount);

        BlockPos center = weasel.workCenter();
        var positions = new LinkedHashSet<BlockPos>();
        var discovered = WeaselChests.discover(level, center, player);
        for (var site : WeaselChests.resolve(level, discovered, player)) {
            BlockPos pos = site.pos();
            positions.add(pos);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                // Discovery returns one canonical position for a double chest.
                positions.add(pos.relative(ChestBlock.getConnectedDirection(state)));
            }
        }
        long[] preview = new long[3 + positions.size()];
        preview[0] = request[0];
        preview[1] = weasel.getId();
        preview[2] = center.asLong();
        int index = 3;
        for (BlockPos position : positions) preview[index++] = position.asLong();

        var payload = new CompoundTag();
        payload.putLongArray(KEY, preview);
        send.accept(payload);
    }
}

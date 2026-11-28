package yagen.waitmydawn.yagens_mobs.entity.weasel.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.entity.weasel.WeaselWorkPreview;

final class WorkPreviewClient {
    private static final int CENTER_COLOR = 0xffffc461;
    private static final int CONTAINER_COLOR = 0xff64e1d8;
    private static LocalPlayer viewer;
    private static int rider;
    private static int nextRequest;
    private static int expires;
    private static long serial;
    private static long firstRequest;
    private static long lastAccepted;
    private static @Nullable View view;

    private record View(BlockPos center, List<BlockPos> containers) {}

    private WorkPreviewClient() {}

    static void tick(Minecraft game, Consumer<CompoundTag> send) {
        var player = game.player;
        var weasel = player == null ? null : WeaselWorkPreview.carriedAdult(player);
        if (weasel == null) {
            viewer = null;
            view = null;
            return;
        }
        if (viewer != player || rider != weasel.getId()) {
            view = null;
            viewer = player;
            rider = weasel.getId();
            nextRequest = player.tickCount;
            firstRequest = serial + 1;
        }
        if (player.tickCount >= nextRequest) {
            nextRequest = player.tickCount + WeaselWorkPreview.REFRESH_TICKS;
            var request = new CompoundTag();
            request.putLongArray(WeaselWorkPreview.KEY, new long[] {++serial});
            send.accept(request);
        }
    }

    static boolean accept(Minecraft game, CompoundTag payload) {
        if (payload == null || !(payload.get(WeaselWorkPreview.KEY) instanceof LongArrayTag data)) return false;
        long[] packed = data.getAsLongArray();
        var player = game.player;
        var weasel = player == null ? null : WeaselWorkPreview.carriedAdult(player);
        if (packed.length < 3 || weasel == null || player != viewer || packed[0] < firstRequest
                || packed[0] <= lastAccepted || packed[0] > serial || packed[1] != rider || weasel.getId() != rider) return true;

        lastAccepted = packed[0];
        var containers = new ArrayList<BlockPos>(packed.length - 3);
        for (int i = 3; i < packed.length; i++) containers.add(BlockPos.of(packed[i]));
        view = new View(BlockPos.of(packed[2]), List.copyOf(containers));
        expires = player.tickCount + 3 * WeaselWorkPreview.REFRESH_TICKS;
        return true;
    }

    static @Nullable Geometry geometry(Minecraft game) {
        var player = game.player;
        if (view == null || player == null || player != viewer || !player.isShiftKeyDown()
                || player.tickCount >= expires) return null;
        var weasel = WeaselWorkPreview.carriedAdult(player);
        return weasel != null && weasel.getId() == rider ? new Geometry(view) : null;
    }

    @FunctionalInterface
    interface Line {
        void draw(double x, double y, double z, int axis, double end, int color);
    }

    record Geometry(View view) {
        void render(Line line) {
            box(view.center(), .045, CENTER_COLOR, line);
            for (BlockPos container : view.containers()) box(container, .015, CONTAINER_COLOR, line);
        }
    }

    private static void box(BlockPos pos, double padding, int color, Line line) {
        double x = pos.getX() - padding, y = pos.getY() - padding, z = pos.getZ() - padding;
        double width = 1 + 2 * padding;
        for (int corner = 0; corner < 4; corner++) {
            double a = (corner & 1) * width, b = (corner >>> 1) * width;
            line.draw(x, y + a, z + b, 0, x + width, color);
            line.draw(x + a, y, z + b, 1, y + width, color);
            line.draw(x + a, y + b, z, 2, z + width, color);
        }
    }
}

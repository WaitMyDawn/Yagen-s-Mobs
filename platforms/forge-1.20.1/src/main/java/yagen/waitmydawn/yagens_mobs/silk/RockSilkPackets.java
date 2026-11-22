package yagen.waitmydawn.yagens_mobs.silk;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;

public final class RockSilkPackets {
    private static final Map<ServerPlayer, Integer> LAST_GRAB = new WeakHashMap<>();

    private RockSilkPackets() {
    }

    public static void handleGrab(Grab payload, ServerPlayer player) {
        if (player != null && Float.isFinite(payload.yaw())
                && Float.isFinite(payload.pitch()) && Math.abs(payload.pitch()) <= 90
                && player.containerMenu == player.inventoryMenu
                && !(player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                .getItem() instanceof SilkLauncher)) {
            tryGrab(player, Vec3.directionFromRotation(payload.pitch(), payload.yaw()));
        }
    }

    public static void handleInput(Input payload, ServerPlayer player) {
        if (player != null && player.getVehicle() instanceof RockSilkGrip grip && grip.getId() == payload.vehicle()) {
            grip.updateInput(player, payload.keys());
        }
    }

    private static void tryGrab(ServerPlayer player, Vec3 view) {
        Integer last = LAST_GRAB.get(player);
        if (last != null && player.tickCount - last < 3) {
            return;
        }
        LAST_GRAB.put(player, player.tickCount);
        var target = RockSilkInteractions.aimedLine(player, view, LegacyNetwork.entityReach(player));
        if (target != null) {
            RockSilkGrip.grab(player, target);
        }
    }

    public record Grab(float yaw, float pitch, boolean offhand) {}

    public record Input(int vehicle, int keys) {}
}

package yagen.waitmydawn.yagens_mobs.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkGrip;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkInteractions;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.silk.SilkLauncher;

public final class RockSilkControls {
    private static int lastVehicle;
    private static int lastKeys;
    private static int inputAge;

    private RockSilkControls() {
    }

    public static boolean tryGrab() {
        var game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || player.getMainHandItem().getItem() instanceof SilkLauncher) {
            return false;
        }
        double range = player.entityInteractionRange();
        if (game.hitResult instanceof EntityHitResult hit) {
            range = Math.min(range, player.getEyePosition().distanceTo(hit.getLocation()));
        }
        var aimed = RockSilkInteractions.aimedLine(player, player.getViewVector(1), range);
        if (aimed != null) {
            ClientPlayNetworking.send(new RockSilkPackets.Grab(player.getYRot(), player.getXRot(),
                    false));
            return true;
        }
        return false;
    }

    public static void tick(Minecraft game) {
        if (game.player == null || !(game.player.getVehicle() instanceof RockSilkGrip grip)) {
            lastVehicle = 0;
            return;
        }
        var options = game.options;
        int keys = game.gui.screen() != null ? 0 : key(options.keyUp, RockSilkGrip.FORWARD) | key(options.keyDown, RockSilkGrip.BACK)
                | key(options.keyLeft, RockSilkGrip.LEFT) | key(options.keyRight, RockSilkGrip.RIGHT)
                | key(options.keySprint, RockSilkGrip.SPRINT) | key(options.keyJump, RockSilkGrip.JUMP)
                | key(options.keyShift, RockSilkGrip.RELEASE);
        if (keys != lastKeys || lastVehicle != grip.getId() || ++inputAge >= 8) {
            ClientPlayNetworking.send(new RockSilkPackets.Input(grip.getId(), keys));
            lastVehicle = grip.getId();
            lastKeys = keys;
            inputAge = 0;
        }
    }

    private static int key(KeyMapping key, int bit) {
        return key.isDown() ? bit : 0;
    }
}


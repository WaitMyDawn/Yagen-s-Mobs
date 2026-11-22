package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkGrip;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkInteractions;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.silk.SilkLauncher;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class RockSilkControls {
    private static int lastVehicle;
    private static int lastKeys;
    private static int inputAge;

    private RockSilkControls() {
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        var game = Minecraft.getInstance();
        var player = game.player;
        if (!event.isUseItem() || player == null || player.getItemInHand(event.getHand()).getItem() instanceof SilkLauncher) {
            return;
        }
        double range = player.entityInteractionRange();
        if (game.hitResult instanceof EntityHitResult hit) {
            range = Math.min(range, player.getEyePosition().distanceTo(hit.getLocation()));
        }
        var aimed = RockSilkInteractions.aimedLine(player, player.getViewVector(1), range);
        if (aimed != null) {
            PacketDistributor.sendToServer(new RockSilkPackets.Grab(player.getYRot(), player.getXRot(),
                    event.getHand() == InteractionHand.OFF_HAND));
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var game = Minecraft.getInstance();
        if (game.player == null || !(game.player.getVehicle() instanceof RockSilkGrip grip)) {
            lastVehicle = 0;
            return;
        }
        var options = game.options;
        int keys = game.screen != null ? 0 : key(options.keyUp, RockSilkGrip.FORWARD) | key(options.keyDown, RockSilkGrip.BACK)
                | key(options.keyLeft, RockSilkGrip.LEFT) | key(options.keyRight, RockSilkGrip.RIGHT)
                | key(options.keySprint, RockSilkGrip.SPRINT) | key(options.keyJump, RockSilkGrip.JUMP)
                | key(options.keyShift, RockSilkGrip.RELEASE);
        if (keys != lastKeys || lastVehicle != grip.getId() || ++inputAge >= 8) {
            PacketDistributor.sendToServer(new RockSilkPackets.Input(grip.getId(), keys));
            lastVehicle = grip.getId();
            lastKeys = keys;
            inputAge = 0;
        }
    }

    private static int key(KeyMapping key, int bit) {
        return key.isDown() ? bit : 0;
    }
}


package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.Velocipod;
import yagen.waitmydawn.yagens_mobs.entity.VelocipodSprintInput;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
final class VelocipodControls {
    static final ResourceLocation STAMINA = new ResourceLocation(ModIdentity.ID, "velocipod_stamina");
    private static int lastMount = -1;
    private static boolean lastSprint;
    private static int inputAge;

    private VelocipodControls() {
    }

    @SubscribeEvent
    public static void sendInput(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft game = Minecraft.getInstance();
        Velocipod mount = mounted(game);
        if (mount == null) {
            lastMount = -1;
            return;
        }

        // The vanilla sprint command is not sent for a server-controlled vehicle.
        boolean sprint = game.screen == null && game.options.keySprint.isDown();
        if (lastMount != mount.getId() || lastSprint != sprint || ++inputAge >= 8) {
            LegacyNetwork.sendToServer(new VelocipodSprintInput(mount.getId(), sprint));
            lastMount = mount.getId();
            lastSprint = sprint;
            inputAge = 0;
        }
    }

    @SubscribeEvent
    public static void replaceExperience(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) && mounted(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    static void renderStamina(GuiGraphics graphics) {
        Minecraft game = Minecraft.getInstance();
        Velocipod mount = mounted(game);
        if (mount == null || game.options.hideGui) {
            return;
        }
        float stamina = mount.staminaPercent();
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 29;
        int full = 180;
        int fill = Mth.clamp(Mth.floor(stamina / 100 * full), 0, full);
        boolean low = stamina < 30;
        graphics.fill(x, y, x + 182, y + 5, 0xFF181C19);
        graphics.fill(x + 1, y + 1, x + 1 + full, y + 4, 0xFF3C4234);
        if (fill > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fill, y + 4, low ? 0xFFD58052 : 0xFF69B691);
            graphics.fill(x + 1, y + 1, x + 1 + fill, y + 2, low ? 0xFFF1AE79 : 0xFF9DD7AD);
        }
        int sprintLine = x + 1 + full * 30 / 100;
        graphics.fill(sprintLine, y, sprintLine + 1, y + 5, 0xFFDDD09D);
    }

    private static Velocipod mounted(Minecraft game) {
        return game.player != null && game.player.getVehicle() instanceof Velocipod pod ? pod : null;
    }
}


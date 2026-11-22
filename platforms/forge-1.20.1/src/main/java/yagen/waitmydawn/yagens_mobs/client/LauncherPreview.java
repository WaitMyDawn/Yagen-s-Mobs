package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPath;
import yagen.waitmydawn.yagens_mobs.silk.SilkLauncher;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class LauncherPreview {
    private static RockSilkPath.Aim aim;
    private static RockSilkPath.Pin first;
    private static int budget;
    private static RockSilkPath.Drop drop;

    private LauncherPreview() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var game = Minecraft.getInstance();
        var player = game.player;
        aim = null;
        first = null;
        drop = null;
        if (player == null || player.isSpectator() || game.screen != null) {
            return;
        }
        ItemStack stack = player.getMainHandItem().getItem() instanceof SilkLauncher
                ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.getItem() instanceof SilkLauncher) {
            aim = SilkLauncher.preview(player, stack);
            first = SilkLauncher.pendingPin(player, stack);
            budget = SilkLauncher.available(stack);
            if (aim.status() == RockSilkPath.Status.HANG && first != null) {
                drop = RockSilkPath.drop(player.level(), first, budget);
            }
        }
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (aim == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || aim.status() == RockSilkPath.Status.NO_TARGET) {
            return;
        }
        int color = color();
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        var cam = camera.getPosition();
        var game = Minecraft.getInstance();
        var buffers = game.renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (drop != null) {
            if (drop.spools() > 0) {
                line(vertices, pose.last(), first.tip(), drop.end(), color);
            }
        } else {
            var pin = aim.pin();
            Vec3 center = aim.marker();
            Vec3 axis = pin == null ? cam.subtract(center).normalize() : Vec3.atLowerCornerOf(pin.face().getNormal());
            center = center.add(axis.scale(0.025));
            Vec3 left = axis.cross(new Vec3(0, 1, 0));
            left = (left.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : left.normalize()).scale(0.23);
            Vec3 up = axis.cross(left);
            Vec3 a = center.add(left).add(up);
            Vec3 b = center.subtract(left).add(up);
            Vec3 c = center.subtract(left).subtract(up);
            Vec3 d = center.add(left).subtract(up);
            line(vertices, pose.last(), a, b, color);
            line(vertices, pose.last(), b, c, color);
            line(vertices, pose.last(), c, d, color);
            line(vertices, pose.last(), d, a, color);
            if (first != null && center.distanceToSqr(first.tip()) > 0.01) {
                line(vertices, pose.last(), first.tip(), center, color);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (aim == null) {
            return;
        }
        var game = Minecraft.getInstance();
        if (game.options.hideGui) {
            return;
        }
        var graphics = event.getGuiGraphics();
        Component hint;
        if (drop != null) {
            hint = drop.spools() == 0 ? Component.translatable("message.yagens_mobs.silk_launcher.no_drop")
                    : Component.translatable(SilkLauncher.statusKey(aim.status()),
                            Math.round(first.tip().distanceTo(drop.end())), drop.spools());
        } else {
            hint = Component.translatable(SilkLauncher.statusKey(aim.status()), aim.spools(), budget);
        }
        graphics.drawCenteredString(game.font, hint, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 12, color());
    }

    private static int color() {
        return switch (aim.status()) {
            case READY -> 0xff82e1a2;
            case HANG -> 0xfff0c36b;
            case NO_TARGET -> 0xffdddddd;
            default -> 0xffff6666;
        };
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from).normalize();
        buffer.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z).color((color >>> 16) & 255, (color >>> 8) & 255, color & 255, color >>> 24)
                .normal(pose.normal(), (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
        buffer.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z).color((color >>> 16) & 255, (color >>> 8) & 255, color & 255, color >>> 24)
                .normal(pose.normal(), (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }
}


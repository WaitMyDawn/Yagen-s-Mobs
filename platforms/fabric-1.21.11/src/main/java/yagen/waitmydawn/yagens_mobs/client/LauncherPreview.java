package yagen.waitmydawn.yagens_mobs.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPath;
import yagen.waitmydawn.yagens_mobs.silk.SilkLauncher;

public final class LauncherPreview {
    private static RockSilkPath.Aim aim;
    private static RockSilkPath.Pin first;
    private static int budget;
    private static RockSilkPath.Drop drop;

    private LauncherPreview() {
    }

    public static void tick(Minecraft ignored) {
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
            aim = SilkLauncher.preview(player, stack, game.options.getEffectiveRenderDistance());
            first = SilkLauncher.pendingPin(player, stack);
            budget = SilkLauncher.available(stack);
            if (aim.status() == RockSilkPath.Status.HANG && first != null) {
                drop = RockSilkPath.drop(player.level(), first, budget);
            }
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        if (aim == null
                || aim.status() == RockSilkPath.Status.NO_TARGET) {
            return;
        }
        int color = color();
        var pose = context.matrices();
        var cam = context.worldState().cameraRenderState.pos;
        var buffers = context.consumers();
        var vertices = buffers.getBuffer(RenderTypes.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (drop != null) {
            if (drop.spools() > 0) {
                line(vertices, pose.last(), first.tip(), drop.end(), color);
            }
        } else {
            var pin = aim.pin();
            Vec3 center = aim.marker();
            Vec3 axis = pin == null ? cam.subtract(center).normalize() : Vec3.atLowerCornerOf(pin.face().getUnitVec3i());
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
    }

    public static void renderHud(GuiGraphics graphics) {
        if (aim == null) {
            return;
        }
        var game = Minecraft.getInstance();
        if (game.options.hideGui) {
            return;
        }
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
        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(color)
                .setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z).setLineWidth(2.5F);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(color)
                .setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z).setLineWidth(2.5F);
    }
}


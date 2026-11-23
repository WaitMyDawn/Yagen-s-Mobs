package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPath;

@Mod.EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
final class SalvageLinkOverlay {
    private static List<SalvageNestDirectory.Link> links = List.of();
    private static String dimension = "";
    private static int poll;
    private static SalvageNestDirectory.Link pointed;

    private SalvageLinkOverlay() {}

    static void accept(CompoundTag data) {
        dimension = data.getString("Dimension");
        links = data.getList("Links", Tag.TAG_COMPOUND).stream().map(tag -> SalvageNestDirectory.Link.read((CompoundTag) tag))
                .filter(Objects::nonNull).toList();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var game = Minecraft.getInstance();
        if (game.player == null || !SalvageRoutes.hasCompass(game.player)) {
            links = List.of();
            poll = 0;
            pointed = null;
            return;
        }
        if (poll-- <= 0) {
            poll = 20;
            LegacyNetwork.sendToServer(new SalvageRoutes.Request(SalvageRoutes.action("Overlay")));
        }
        pointed = pick();
    }

    private static boolean visible(Minecraft game) {
        return game.player != null && game.level != null && SalvageRoutes.hasCompass(game.player)
                && dimension.equals(game.level.dimension().location().toString());
    }

    private static SalvageNestDirectory.Link pick() {
        var game = Minecraft.getInstance();
        if (!visible(game) || !game.player.isShiftKeyDown()) {
            return null;
        }
        if (game.hitResult instanceof BlockHitResult hit && game.level.getBlockState(hit.getBlockPos()).is(SalvageRays.NEST_BOX.get())) {
            return null;
        }
        Vec3 eye = game.player.getEyePosition();
        Vec3 aim = game.player.getViewVector(1);
        double reach = RockSilkPath.visibleReach(game.player, eye, eye.add(aim.scale(game.player.getBlockReach())));
        Vec3 view = aim.scale(reach);
        if (reach <= .1) {
            return null;
        }
        return links.stream().map(link -> new Pick(link, SalvageRoutes.pickFraction(link, eye, view)))
                .filter(pick -> pick.fraction() < 1).min(Comparator.comparingDouble(Pick::fraction))
                .map(Pick::link).orElse(null);
    }

    private record Pick(SalvageNestDirectory.Link link, double fraction) {}

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        var game = Minecraft.getInstance();
        if (!event.isUseItem() || game.player == null || !game.player.getItemInHand(event.getHand()).is(Items.COMPASS)) {
            return;
        }
        var link = pick();
        if (link != null) {
            var data = SalvageRoutes.action("CutLine");
            data.put("Link", link.save());
            LegacyNetwork.sendToServer(new SalvageRoutes.Request(data));
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var game = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible(game) || links.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = game.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var link : links) {
            draw(lines, pose.last(), link.a().marker(), link.b().marker(), link.equals(pointed) ? 0xffffb284 : 0xffcd99ff);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void draw(VertexConsumer output, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from).normalize();
        for (Vec3 end : new Vec3[]{from, to}) {
            output.vertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                    .color(color).normal(pose.normal(), (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
        }
    }
}

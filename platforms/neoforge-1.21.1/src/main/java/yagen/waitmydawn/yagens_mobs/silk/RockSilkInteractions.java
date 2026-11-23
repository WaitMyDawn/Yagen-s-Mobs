package yagen.waitmydawn.yagens_mobs.silk;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SegmentApproach;

@EventBusSubscriber(modid = ModIdentity.ID)
public final class RockSilkInteractions {
    // A valid span can start farther away than a hand-fired pin when a courier carried the far end.
    private static final double ANCHOR_SEARCH = 32 * 16;
    private static final double PIN_RADIUS = 0.16;
    private static final double GRAB_RADIUS = 0.19;
    private static final double PIN_CLEARANCE = 0.33;

    public record GripTarget(RockSilkLine line, double fraction, double rayDistance) {
    }

    private RockSilkInteractions() {
    }

    @SubscribeEvent
    public static void recoverFromBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof SilkLauncher || event.getEntity().isShiftKeyDown()) {
            return;
        }
        var player = event.getEntity();
        RockSilkLine line = aimedPin(player);
        if (line != null) {
            if (!player.level().isClientSide) {
                line.recover(player);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            event.setCanceled(true);
        }
    }

    public static @Nullable GripTarget aimedLine(Player player, Vec3 view, double reach) {
        if (player.isSpectator() || player.isPassenger() || player.isShiftKeyDown() || !player.isAlive()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        double visible = RockSilkPath.visibleReach(player, eye, eye.add(view.scale(reach)));
        if (visible <= 1e-4) {
            return null;
        }

        var level = player.level();
        Vec3 ray = view.scale(visible);
        AABB trace = new AABB(eye, eye.add(ray)).inflate(GRAB_RADIUS);
        AABB search = new AABB(trace.minX - ANCHOR_SEARCH, level.getMinBuildHeight(), trace.minZ - ANCHOR_SEARCH,
                trace.maxX + ANCHOR_SEARCH, level.getMaxBuildHeight(), trace.maxZ + ANCHOR_SEARCH);
        GripTarget nearest = null;
        for (var line : level.getEntitiesOfClass(RockSilkLine.class, search, RockSilkLine::supportsGrip)) {
            Vec3 top = line.first().tip();
            Vec3 bottom = line.tip();
            if (!trace.intersects(new AABB(top, bottom).inflate(1e-4))) {
                continue;
            }
            Vec3 span = bottom.subtract(top);
            double length = span.length();
            double trimStart = PIN_CLEARANCE / length;
            double trimEnd = line.second() == null ? 0 : PIN_CLEARANCE / length;
            if (trimStart + trimEnd >= 1) {
                continue;
            }
            Vec3 first = top.add(span.scale(trimStart));
            Vec3 rope = span.scale(1 - trimStart - trimEnd);
            SegmentApproach match = SegmentApproach.between(eye, ray, first, rope);
            double along = match.firstFraction() * visible;
            if (nearest != null && along >= nearest.rayDistance()) {
                continue;
            }
            if (match.distanceSqr() <= GRAB_RADIUS * GRAB_RADIUS) {
                nearest = new GripTarget(line, trimStart + (1 - trimStart - trimEnd) * match.secondFraction(), along);
            }
        }
        return nearest;
    }

    private static @Nullable RockSilkLine aimedPin(Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 view = player.getViewVector(1);
        Vec3 end = from.add(view.scale(player.blockInteractionRange()));
        double distance = RockSilkPath.visibleReach(player, from, end);
        end = from.add(view.scale(distance));
        // The line entity is indexed by its first pin.
        AABB hosts = new AABB(from, end).inflate(ANCHOR_SEARCH + PIN_RADIUS);
        RockSilkLine nearest = null;
        for (var line : player.level().getEntitiesOfClass(RockSilkLine.class, hosts,
                candidate -> candidate.canRecover(player))) {
            double hit = hitDistance(from, end, line.first().tip());
            var second = line.second();
            if (second != null) {
                hit = Math.min(hit, hitDistance(from, end, second.tip()));
            }
            if (hit < distance) {
                distance = hit;
                nearest = line;
            }
        }
        return nearest;
    }

    private static double hitDistance(Vec3 from, Vec3 to, Vec3 center) {
        return AABB.ofSize(center, PIN_RADIUS * 2, PIN_RADIUS * 2, PIN_RADIUS * 2).clip(from, to)
                .map(from::distanceTo).orElse(Double.POSITIVE_INFINITY);
    }
}


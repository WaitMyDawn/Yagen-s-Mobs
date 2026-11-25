package yagen.waitmydawn.yagens_mobs.entity.rockhanger;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.core.SegmentApproach;

public final class RockHangerSilkInteraction {
    private static final double SILK_HIT_RADIUS = 0.2;

    private RockHangerSilkInteraction() {
    }

    public static InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(Items.SHEARS)) {
            return InteractionResult.PASS;
        }
        var level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 aim = player.getViewVector(1);
        Vec3 end = eye.add(aim.scale(player.entityInteractionRange()));
        Vec3 visibleEnd = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        AABB rays = new AABB(eye, visibleEnd).inflate(SILK_HIT_RADIUS);
        double padding = RockHanger.FORAGING_RADIUS + SILK_HIT_RADIUS;
        AABB columns = new AABB(rays.minX - padding, level.getMinBuildHeight(), rays.minZ - padding,
                rays.maxX + padding, level.getMaxBuildHeight(), rays.maxZ + padding);
        RockHanger nearest = null;
        Vec3 ray = visibleEnd.subtract(eye);
        double nearestFraction = 1;
        for (RockHanger hanger : level.getEntitiesOfClass(RockHanger.class, columns, RockHanger::hasShearableTether)) {
            Vec3 anchor = hanger.silkTop();
            var hit = SegmentApproach.between(eye, ray, anchor, hanger.silkBottom().subtract(anchor));
            if (hit.distanceSqr() <= SILK_HIT_RADIUS * SILK_HIT_RADIUS && hit.firstFraction() < nearestFraction) {
                nearestFraction = hit.firstFraction();
                nearest = hanger;
            }
        }
        if (nearest != null) {
            if (!level.isClientSide) {
                nearest.shear(player, hand, true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}

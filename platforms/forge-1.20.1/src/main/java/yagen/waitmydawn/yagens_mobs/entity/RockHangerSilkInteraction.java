package yagen.waitmydawn.yagens_mobs.entity;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.core.SegmentApproach;

@EventBusSubscriber(modid = ModIdentity.ID)
public final class RockHangerSilkInteraction {
    private static final double SILK_HIT_RADIUS = 0.2;

    private RockHangerSilkInteraction() {
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        interact(event);
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        interact(event);
    }

    private static void interact(PlayerInteractEvent event) {
        if (!event.getItemStack().is(Items.SHEARS)) {
            return;
        }
        var player = event.getEntity();
        var level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 aim = player.getViewVector(1);
        Vec3 end = eye.add(aim.scale(player.getEntityReach()));
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
                nearest.shear(player, event.getHand(), true);
            }
            if (event instanceof PlayerInteractEvent.RightClickItem item) {
                item.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
                item.setCanceled(true);
            } else if (event instanceof PlayerInteractEvent.RightClickBlock block) {
                block.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
                block.setCanceled(true);
            }
        }
    }
}


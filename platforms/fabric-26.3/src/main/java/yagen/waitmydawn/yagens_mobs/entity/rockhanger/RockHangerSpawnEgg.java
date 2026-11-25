package yagen.waitmydawn.yagens_mobs.entity.rockhanger;

import net.minecraft.world.item.SpawnEggItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class RockHangerSpawnEgg extends SpawnEggItem {
    public RockHangerSpawnEgg(Properties properties) {
        super(properties.spawnEgg(ModEntities.ROCK_HANGER.get()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        if (!(context.getLevel() instanceof ServerLevel level)
                || !spawnsEntity(stack, ModEntities.ROCK_HANGER.get())) {
            return super.useOn(context);
        }
        var clicked = context.getClickedPos();
        if (level.getBlockEntity(clicked) instanceof Spawner) {
            return super.useOn(context);
        }
        var player = context.getPlayer();
        Direction face = context.getClickedFace();
        if (player != null && (!level.mayInteract(player, clicked)
                || !player.mayUseItemAt(clicked.relative(face), face, stack))) {
            return InteractionResult.FAIL;
        }

        var type = ModEntities.ROCK_HANGER.get();
        boolean hang = face == Direction.DOWN && RockHangerSurfaces.canHangFromEgg(level, clicked, type);
        boolean empty = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty();
        BlockPos spawn = hang ? BlockPos.containing(RockHangerSurfaces.ceilingFeet(clicked, type))
                : empty ? clicked : clicked.relative(face);
        var mob = type.create(level, EntityType.createDefaultStackConfig(level, stack, player), spawn,
                EntitySpawnReason.SPAWN_ITEM_USE, !hang, !empty && face == Direction.UP);
        if (mob == null) {
            return InteractionResult.FAIL;
        }
        if (hang) {
            mob.spawnSuspended(clicked);
        }
        boolean fits = hang ? fits(mob, mob.position()) : fitGroundPlacement(mob, empty ? Direction.UP : face);
        if (!fits || !level.tryAddFreshEntityWithPassengers(mob)) {
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, mob.position());
        return InteractionResult.CONSUME;
    }

    private static boolean fitGroundPlacement(RockHanger mob, Direction face) {
        Vec3 pos = mob.position();
        if (fits(mob, pos)) {
            return true;
        }

        // A wide mob placed at the center of the adjacent cell still overlaps the clicked wall.
        if (face.getAxis().isHorizontal()) {
            double overhang = Math.max(0, mob.getBbWidth() / 2.0 - 0.5) + 0.035;
            var outset = pos.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(overhang));
            if (fits(mob, outset)) {
                mob.setPos(outset);
                return true;
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 shifted = pos.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.25));
            if (fits(mob, shifted)) {
                mob.setPos(shifted);
                return true;
            }
        }
        return false;
    }

    private static boolean fits(RockHanger mob, Vec3 pos) {
        var level = mob.level();
        var box = mob.getBoundingBox().move(pos.subtract(mob.position()));
        return level.getWorldBorder().isWithinBounds(box) && RockHangerSurfaces.clear(level, box);
    }
}


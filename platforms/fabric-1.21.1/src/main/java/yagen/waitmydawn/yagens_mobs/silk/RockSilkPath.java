package yagen.waitmydawn.yagens_mobs.silk;

import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RockSilkPath {

    private static final TagKey<Block> GLASS_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));
    public static final int REACH = 64;
    public static final int PER_SPOOL = 32;
    private static final int PREVIEW_DISTANCE = 96;
    private static final double EPSILON = 0.001;

    public enum Status { READY, NO_SPOOLS, OUT_OF_RANGE, UNSUPPORTED, OBSTRUCTED, UNLOADED, NO_TARGET, HANG }

    public record Pin(BlockPos block, Direction face) {
        public Vec3 tip() {
            return Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.56));
        }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Block", block.asLong());
            tag.putByte("Face", (byte) face.get3DDataValue());
            return tag;
        }

        static Pin load(CompoundTag tag) {
            return new Pin(BlockPos.of(tag.getLong("Block")), Direction.from3DDataValue(tag.getByte("Face")));
        }
    }

    public record Aim(Status status, Vec3 marker, @Nullable Pin pin, int spools) {
        public boolean canPin() {
            return status == Status.READY && pin != null;
        }
    }

    public record Drop(Vec3 end, int spools) {
    }

    private record Trace(@Nullable BlockHitResult hit, @Nullable Vec3 unloaded) {
    }

    private RockSilkPath() {
    }

    public static Aim aim(Player player, @Nullable Pin first, int available) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1);
        Vec3 limit = eye.add(view.scale(REACH));
        Trace trace = trace(level, eye, eye.add(view.scale(PREVIEW_DISTANCE)), ClipContext.Block.OUTLINE);
        if (trace.unloaded() != null && eye.distanceToSqr(trace.unloaded()) <= REACH * REACH) {
            return new Aim(Status.UNLOADED, trace.unloaded(), null, 0);
        }
        var hit = trace.hit();
        if (hit == null) {
            return new Aim(first == null ? Status.NO_TARGET : Status.HANG, limit, null, 0);
        }
        Vec3 point = hit.getLocation();
        if (eye.distanceToSqr(point) > REACH * REACH + EPSILON) {
            return new Aim(Status.OUT_OF_RANGE, point, null, 0);
        }
        var pin = new Pin(hit.getBlockPos(), hit.getDirection());
        if (!supports(level, pin) || !level.getWorldBorder().isWithinBounds(pin.block())) {
            return new Aim(Status.UNSUPPORTED, point, pin, 0);
        }
        double length = first == null ? 0 : first.tip().distanceTo(pin.tip());
        int spools = Math.max(1, (int) Math.ceil((length - EPSILON) / PER_SPOOL));
        if (length > REACH + EPSILON) {
            return new Aim(Status.OUT_OF_RANGE, pin.tip(), pin, spools);
        }
        if (first != null && (length < 0.25 || !clear(level, first.tip(), pin.tip()))) {
            return new Aim(Status.OBSTRUCTED, pin.tip(), pin, spools);
        }
        return new Aim(spools <= available ? Status.READY : Status.NO_SPOOLS, pin.tip(), pin, spools);
    }

    public static boolean supports(Level level, Pin pin) {
        var pos = pin.block();
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        var state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES) || state.is(GLASS_BLOCKS) || state.is(GLASS_PANES)) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            var type = state.getValue(SlabBlock.TYPE);
            return type == SlabType.DOUBLE || type == SlabType.BOTTOM && pin.face() == Direction.DOWN
                    || type == SlabType.TOP && pin.face() == Direction.UP;
        }
        return Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    static boolean clear(Level level, Vec3 from, Vec3 to) {
        var obstruction = trace(level, from, to, ClipContext.Block.COLLIDER);
        return obstruction.hit() == null && obstruction.unloaded() == null;
    }

    public static Drop drop(Level level, Pin first, int available) {
        Vec3 from = first.tip();
        Vec3 bottom = new Vec3(from.x, Math.max(level.getMinBuildHeight() + 0.02,
                from.y - (double) available * PER_SPOOL), from.z);
        var obstruction = trace(level, from, bottom, ClipContext.Block.COLLIDER);
        Vec3 end = obstruction.hit() != null ? obstruction.hit().getLocation().add(0, 0.025, 0) : bottom;
        double length = from.y - end.y;
        return new Drop(end, length < 0.22 ? 0 : Math.max(1, (int) Math.ceil((length - EPSILON) / PER_SPOOL)));
    }

    public static double visibleReach(Player player, Vec3 eye, Vec3 end) {
        var trace = trace(player.level(), eye, end, ClipContext.Block.COLLIDER);
        if (trace.hit() != null) {
            return eye.distanceTo(trace.hit().getLocation());
        }
        return trace.unloaded() != null ? eye.distanceTo(trace.unloaded()) : eye.distanceTo(end);
    }

    private static Trace trace(Level level, Vec3 from, Vec3 to, ClipContext.Block shape) {
        return BlockGetter.traverseBlocks(from, to, new ClipContext(from, to, shape, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()), (context, cursor) -> {
            if (level.isOutsideBuildHeight(cursor)) {
                return null;
            }
            if (!level.hasChunkAt(cursor)) {
                return new Trace(null, new AABB(cursor).inflate(EPSILON).clip(from, to).orElse(from));
            }
            var state = level.getBlockState(cursor);
            var hit = context.getBlockShape(state, level, cursor).clip(from, to, cursor);
            return hit != null ? new Trace(hit, null) : null;
        }, context -> new Trace(null, null));
    }
}


package yagen.waitmydawn.yagens_mobs.transport;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public record Cargo(List<Part> parts) {
    private static final int MAX_REGION_VOLUME = 64;
    private static final int QUIET_UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public Cargo {
        parts = List.copyOf(parts);
    }

    public int size() {
        return parts.size();
    }

    int chestMate(int index) {
        Part part = parts.get(index);
        if (!(part.state().getBlock() instanceof ChestBlock) || part.state().getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return -1;
        }
        BlockPos linked = pairedPosition(part.offset(), part.state());
        for (int i = 0; i < parts.size(); i++) {
            Part other = parts.get(i);
            if (other.offset().equals(linked) && other.state().is(part.state().getBlock())
                    && other.state().getValue(ChestBlock.FACING) == part.state().getValue(ChestBlock.FACING)
                    && other.state().getValue(ChestBlock.TYPE) != part.state().getValue(ChestBlock.TYPE)
                    && part.offset().equals(pairedPosition(other.offset(), other.state()))) {
                return i;
            }
        }
        return -1;
    }

    public static BlockState faceChestPartner(BlockState half, Direction towardPartner) {
        ChestType type = half.getValue(ChestBlock.TYPE);
        return half.setValue(ChestBlock.FACING,
                type == ChestType.LEFT ? towardPartner.getCounterClockWise() : towardPartner.getClockWise());
    }

    public boolean needsTongs() {
        return size() != 1 || parts.getFirst().state().hasBlockEntity()
                || parts.getFirst().state().getBlock().asItem() == net.minecraft.world.item.Items.AIR;
    }

    public static @Nullable Cargo fromItem(ServerLevel level, BlockPos pos, ItemStack stack, BlockState state) {
        if (!(stack.getItem() instanceof BlockItem) || !canCarry(state)) {
            return null;
        }
        BlockState withProperties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(state);
        if (!canCarry(withProperties)
                || withProperties.getBlock() instanceof ChestBlock && withProperties.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return null;
        }
        var main = fromItemPart(level, BlockPos.ZERO, pos, withProperties, stack);
        if (main == null) {
            return null;
        }
        BlockPos paired = pairedPosition(pos, withProperties);
        if (paired == null) {
            return new Cargo(List.of(main));
        }
        BlockState other;
        if (withProperties.getBlock() instanceof BedBlock && withProperties.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            other = withProperties.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        } else if (withProperties.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && withProperties.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            other = withProperties.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        } else {
            return null;
        }
        var secondary = fromItemPart(level, paired.subtract(pos), paired, other, stack);
        return secondary == null ? null : new Cargo(List.of(main, secondary));
    }

    private static @Nullable Part fromItemPart(ServerLevel level, BlockPos offset, BlockPos pos, BlockState state, ItemStack stack) {
        CompoundTag snapshot = null;
        if (state.getBlock() instanceof EntityBlock entityBlock) {
            var entity = entityBlock.newBlockEntity(pos, state);
            if (entity == null) {
                return null;
            }
            var blockData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (blockData != null) {
                blockData.loadInto(entity, level.registryAccess());
            }
            entity.applyComponentsFromItemStack(stack);
            snapshot = entity.saveWithFullMetadata(level.registryAccess());
        }
        return new Part(offset, state, snapshot);
    }

    public static @Nullable Cargo capture(ServerLevel level, ServerPlayer player, BlockPos origin) {
        var positions = new LinkedHashSet<BlockPos>();
        positions.add(origin.immutable());
        BlockState state = level.getBlockState(origin);
        BlockPos partner = pairedPosition(origin, state);
        if (partner != null) {
            if (!level.hasChunkAt(partner)) {
                return null;
            }
            positions.add(partner);
        } else if (!state.hasBlockEntity()) {
            return null;
        }
        return capturePositions(level, player, positions);
    }

    static @Nullable Cargo captureRegion(ServerLevel level, ServerPlayer player, BlockPos start, BlockPos end) {
        long width = Math.abs((long) start.getX() - end.getX()) + 1;
        long height = Math.abs((long) start.getY() - end.getY()) + 1;
        long depth = Math.abs((long) start.getZ() - end.getZ()) + 1;
        if (width > MAX_REGION_VOLUME || height > MAX_REGION_VOLUME || depth > MAX_REGION_VOLUME
                || width * height * depth > MAX_REGION_VOLUME) {
            return null;
        }
        var positions = new LinkedHashSet<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
                return null;
            }
            if (!level.getBlockState(pos).isAir()) {
                positions.add(pos.immutable());
            }
        }
        return positions.isEmpty() ? null : capturePositions(level, player, positions);
    }

    private static @Nullable Cargo capturePositions(ServerLevel level, ServerPlayer player, Collection<BlockPos> positions) {
        List<Part> parts = new ArrayList<>(positions.size());
        var ordered = positions.stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
        for (BlockPos pos : ordered) {
            BlockState current = level.getBlockState(pos);
            BlockPos partner = pairedPosition(pos, current);
            if (partner != null && (!positions.contains(partner)
                    || !level.getBlockState(partner).is(current.getBlock())
                    || !pos.equals(pairedPosition(partner, level.getBlockState(partner))))) {
                return null;
            }
            if (!canCarry(current) || current.getDestroySpeed(level, pos) < 0 || !level.mayInteract(player, pos)
                    || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, current, level.getBlockEntity(pos))) {
                return null;
            }
            parts.add(snapshot(level, ordered.getFirst(), pos));
        }
        // Clear every block entity before removing the blocks so inventory drops cannot duplicate the snapshots.
        for (BlockPos pos : positions) {
            level.removeBlockEntity(pos);
        }
        for (BlockPos pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET_UPDATE);
        }
        updateNeighbors(level, positions);
        return new Cargo(parts);
    }

    static Part snapshot(ServerLevel level, BlockPos origin, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Part(pos.subtract(origin), level.getBlockState(pos),
                entity == null ? null : entity.saveWithFullMetadata(level.registryAccess()));
    }

    private static boolean canCarry(BlockState state) {
        return !state.isAir() && !state.is(ModBlocks.VULTURE_POST.get()) && !state.is(ModBlocks.VULTURE_NEST.get())
                && !(state.getBlock() instanceof GameMasterBlock)
                && !state.is(Blocks.MOVING_PISTON) && !state.is(Blocks.PISTON_HEAD)
                && (!state.hasProperty(BlockStateProperties.EXTENDED) || !state.getValue(BlockStateProperties.EXTENDED))
                && state.getFluidState().isEmpty();
    }

    @Nullable
    private static BlockPos pairedPosition(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return pos.relative(state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? facing : facing.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    public ItemStack toItem() {
        if (needsTongs()) {
            return ItemStack.EMPTY;
        }
        BlockState state = parts.getFirst().state();
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        BlockItemStateProperties properties = BlockItemStateProperties.EMPTY;
        for (var property : state.getProperties()) {
            properties = properties.with(property, state);
        }
        stack.set(DataComponents.BLOCK_STATE, properties);
        return stack;
    }

    boolean hasGroundSupport(ServerLevel level, BlockPos origin) {
        int lowest = parts.stream().mapToInt(part -> part.offset().getY()).min().orElse(0);
        for (Part part : parts) {
            if (part.offset().getY() == lowest) {
                BlockPos support = origin.offset(part.offset()).below();
                if (!level.hasChunkAt(support) || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                    return false;
                }
            }
        }
        return !parts.isEmpty();
    }

    boolean place(ServerLevel level, BlockPos origin, @Nullable ServerPlayer player) {
        var positions = parts.stream().map(part -> origin.offset(part.offset())).toList();
        for (BlockPos pos : positions) {
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos)).isEmpty()
                    || player != null && !level.mayInteract(player, pos)) {
                return false;
            }
        }
        boolean completed = false;
        try {
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                BlockPos pos = positions.get(i);
                if (!level.setBlock(pos, part.state(), QUIET_UPDATE)) {
                    return false;
                }
                if (part.entity() != null) {
                    var entity = level.getBlockEntity(pos);
                    if (entity == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString()
                            .equals(part.entity().getStringOr("id", ""))) {
                        return false;
                    }
                    var data = part.entity().copy();
                    data.putInt("x", pos.getX());
                    data.putInt("y", pos.getY());
                    data.putInt("z", pos.getZ());
                    entity.loadWithComponents(SaveTag.input(level.registryAccess(), data));
                    entity.setChanged();
                }
            }
            for (int i = 0; i < parts.size(); i++) {
                if (!parts.get(i).state().canSurvive(level, positions.get(i))) {
                    return false;
                }
            }
            completed = true;
        } finally {
            if (!completed) {
                for (BlockPos pos : positions) {
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET_UPDATE);
                }
            }
        }
        updateNeighbors(level, positions);
        return true;
    }

    public boolean deploy(ServerLevel level, BlockPos origin, ServerPlayer player) {
        return place(level, origin, player);
    }

    private static void updateNeighbors(ServerLevel level, Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var entries = new ListTag();
        for (Part part : parts) {
            var entry = NbtUtils.writeBlockState(part.state());
            entry.putLong("Offset", part.offset().asLong());
            if (part.entity() != null) {
                entry.put("Tile", part.entity().copy());
            }
            entries.add(entry);
        }
        tag.put("Parts", entries);
        return tag;
    }

    public static Cargo load(CompoundTag tag, HolderLookup.Provider lookup) {
        return load(tag, lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE));
    }

    public static Cargo load(CompoundTag tag, com.mojang.serialization.DynamicOps<Tag> lookup) {
        var parts = tag.getListOrEmpty("Parts").stream().map(CompoundTag.class::cast)
                .map(entry -> new Part(BlockPos.of(entry.getLongOr("Offset", 0L)), BlockState.CODEC.parse(lookup, entry).result().orElseGet(() -> Blocks.AIR.defaultBlockState()),
                        entry.contains("Tile") ? entry.getCompoundOrEmpty("Tile").copy() : null)).toList();
        return new Cargo(parts);
    }

    public record Part(BlockPos offset, BlockState state, @Nullable CompoundTag entity) {}
}

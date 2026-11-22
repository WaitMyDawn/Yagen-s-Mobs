package yagen.waitmydawn.yagens_mobs.transport;

import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class VulturePostBlock extends BaseEntityBlock {
    public static final MapCodec<VulturePostBlock> CODEC = simpleCodec(VulturePostBlock::new);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty START = BooleanProperty.create("start");
    public static final BooleanProperty END = BooleanProperty.create("end");
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    static final double BEAM_TOP = 14.0 / 16.0;
    private static final UUID UNOWNED = new UUID(0, 0);
    private static final VoxelShape[] SHAPES = createShapes();

    public VulturePostBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(UPPER, false).setValue(AXIS, Direction.Axis.X)
                .setValue(START, true).setValue(END, true));
    }

    @Override
    protected MapCodec<VulturePostBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UPPER, AXIS, START, END);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(UPPER) ? getCollisionShape(state, level, pos, context) : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int flags = (state.getValue(AXIS) == Direction.Axis.Z ? 8 : 0) | (state.getValue(UPPER) ? 4 : 0)
                | (state.getValue(START) ? 2 : 0) | (state.getValue(END) ? 1 : 0);
        return SHAPES[flags];
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int flags = 0; flags < shapes.length; flags++) {
            boolean z = (flags & 8) != 0;
            boolean upper = (flags & 4) != 0;
            int postHeight = upper ? 10 : 16;
            VoxelShape shape = upper ? alongBeam(z, 0, 10, 5, 16, BEAM_TOP * 16, 11) : Shapes.empty();
            if ((flags & 2) != 0) {
                shape = Shapes.or(shape, alongBeam(z, 0, 0, 6, 2, postHeight, 10));
            }
            if ((flags & 1) != 0) {
                shape = Shapes.or(shape, alongBeam(z, 14, 0, 6, 16, postHeight, 10));
            }
            shapes[flags] = shape;
        }
        return shapes;
    }

    private static VoxelShape alongBeam(boolean z, double x1, double y1, double z1, double x2, double y2, double z2) {
        return z ? box(z1, y1, x1, z2, y2, x2) : box(x1, y1, z1, x2, y2, z2);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var above = context.getClickedPos().above();
        if (context.getLevel().isOutsideBuildHeight(above) || !context.getLevel().getBlockState(above).isAir()) {
            return null;
        }
        Direction facing = context.getHorizontalDirection();
        Direction.Axis axis = facing.getClockWise().getAxis();
        var player = context.getPlayer();
        if (context.getLevel() instanceof ServerLevel server && !StationData.get(server).canJoin(
                context.getClickedPos(), player == null ? UNOWNED : player.getUUID(), axis)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.yagens_mobs.station_merge_fuel"), true);
            }
            return null;
        }
        return defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server && !state.getValue(UPPER)) {
            level.setBlock(pos.above(), state.setValue(UPPER, true), Block.UPDATE_ALL);
            StationData.get(server).addPost(server, pos, placer == null ? UNOWNED : placer.getUUID(), state.getValue(AXIS));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StationData.get(level).refreshPost(level, pos);
    }

    @Override
    protected void onRemove(BlockState current, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this) && level instanceof ServerLevel server) {
            if (current.getValue(UPPER)) {
                if (level.getBlockState(pos.below()).is(this)) {
                    level.destroyBlock(pos.below(), true);
                }
            } else {
                StationData.get(server).removePost(server, pos);
                if (level.getBlockState(pos.above()).is(this)) {
                    level.removeBlock(pos.above(), false);
                }
            }
        }
        super.onRemove(current, level, pos, replacement, moving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(UPPER) ? null : new VulturePostEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer user) {
            var lower = state.getValue(UPPER) ? pos.below() : pos;
            if (!state.getValue(UPPER) && player.isShiftKeyDown()) {
                takeOrdinary(server, lower, user);
            } else {
                StationPackets.open(user, lower);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer user) {
            var data = StationData.get(server);
            boolean upper = state.getValue(UPPER);
            var lower = upper ? pos.below() : pos;
            var station = data.station(lower);
            if (station == null) {
                return ItemInteractionResult.FAIL;
            }
            if (stack.getItem() instanceof MailBag bag) {
                bag.onStation(server, user, hand, stack, lower);
            } else if (stack.is(Items.ROTTEN_FLESH) && !user.isShiftKeyDown() && station.owner().equals(user.getUUID())) {
                int moved = data.addFuel(station, stack.getCount());
                stack.consume(moved, user);
                user.displayClientMessage(Component.translatable("message.yagens_mobs.station_fuel", data.fuel(station)), true);
            } else if (upper) {
                StationPackets.open(user, lower);
            } else if (station.owner().equals(user.getUUID()) && stack.getItem() instanceof CargoTongs tongs) {
                tongs.onStation(server, user, stack, station, lower);
            } else if (user.isShiftKeyDown()) {
                takeOrdinary(server, lower, user);
            } else if (station.owner().equals(user.getUUID()) && stack.getItem() instanceof BlockItem block) {
                var destination = block.getBlock().defaultBlockState();
                if (destination.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    destination = destination.setValue(BlockStateProperties.HORIZONTAL_FACING, player.getDirection().getOpposite());
                }
                var cargo = Cargo.fromItem(server, lower, stack, destination);
                if (cargo != null && data.stage(server, station, lower, cargo)) {
                    stack.consume(1, user);
                } else {
                    user.displayClientMessage(Component.translatable("message.yagens_mobs.station_cannot_stage"), true);
                }
            } else {
                StationPackets.open(user, lower);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void takeOrdinary(ServerLevel level, BlockPos pos, ServerPlayer player) {
        var data = StationData.get(level);
        var station = data.station(pos);
        var cargo = data.cargo(pos);
        if (station == null || cargo == null || !station.owner().equals(player.getUUID())) {
            return;
        }
        ItemStack restored = cargo.toItem();
        if (!restored.isEmpty()) {
            data.takeCargo(level, pos);
            player.getInventory().placeItemBackInInventory(restored);
        } else {
            player.displayClientMessage(Component.translatable("message.yagens_mobs.station_use_tongs"), true);
        }
    }
}

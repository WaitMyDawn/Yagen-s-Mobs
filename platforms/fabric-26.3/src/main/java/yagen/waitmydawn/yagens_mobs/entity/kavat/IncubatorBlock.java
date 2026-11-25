package yagen.waitmydawn.yagens_mobs.entity.kavat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public class IncubatorBlock extends Block {
    static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    static final IntegerProperty PART = IntegerProperty.create("part", 1, 7);
    static final double FLOOR = 2 / 16.0;
    protected IncubatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLOSED);
    }

    static BlockPos cell(BlockPos root, int index) {
        return root.offset(index & 1, index >> 2, (index >> 1) & 1);
    }

    private static int index(BlockState state) {
        return state.hasProperty(PART) ? state.getValue(PART) : 0;
    }

    static BlockPos root(BlockPos pos, BlockState state) {
        int i = index(state);
        return pos.offset(-(i & 1), -(i >> 2), -((i >> 1) & 1));
    }

    static boolean matches(BlockState state, int i) {
        return i == 0 ? state.is(ModBlocks.INCUBATOR.get())
                : state.is(ModBlocks.INCUBATOR_PART.get()) && state.getValue(PART) == i;
    }

    static boolean available(Level level, BlockPos pos) {
        var last = cell(pos, 7);
        return !level.isOutsideBuildHeight(pos) && !level.isOutsideBuildHeight(last)
                && level.hasChunksAt(pos, last) && level.getWorldBorder().isWithinBounds(pos)
                && level.getWorldBorder().isWithinBounds(last);
    }

    static boolean occupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).expandTowards(1, 1, 1),
                EntitySelector.NO_SPECTATORS).isEmpty();
    }

    static void install(Level level, BlockPos pos, boolean closed) {
        var part = ModBlocks.INCUBATOR_PART.get().defaultBlockState().setValue(CLOSED, closed);
        for (int i = 1; i < 8; i++)
            level.setBlock(cell(pos, i), part.setValue(PART, i), Block.UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int step = state.getValue(CLOSED) ? IncubatorShape.STEPS : 0;
        BlockPos origin = root(pos, state);
        if (level instanceof Level world && world.hasChunkAt(origin) && world.getBlockEntity(origin) instanceof Incubator machine) {
            if (!state.hasProperty(PART) && machine.isPacked()) return Shapes.block();
            step = Math.round(machine.shutterTravel(0) * IncubatorShape.STEPS / Incubator.SHUTTER_TICKS);
        }
        return IncubatorShape.part(index(state), step);
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && !state.getValue(CLOSED);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModBlocks.INCUBATOR.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer p && level.getBlockEntity(root(pos, state)) instanceof Incubator inc) {
            if (!inc.ensureChamber()) p.sendOverlayMessage(Component.translatable("kavat.chamber_space"));
            p.openMenu(new SimpleMenuProvider((id, inv, viewer) -> new IncubatorMenu(id, inv, inc), ModBlocks.INCUBATOR.get().getName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel server, BlockPos pos, boolean moved) {
        if (!state.is(server.getBlockState(pos).getBlock())) {
            BlockPos origin = root(pos, state);
            if (index(state) == 0) {
                for (int i = 1; i < 8; i++) {
                    BlockPos other = cell(origin, i);
                    if (matches(server.getBlockState(other), i)) server.removeBlock(other, false);
                }
            } else if (matches(server.getBlockState(origin), 0)) {
                server.removeBlock(origin, false);
            }
        }
        super.affectNeighborsAfterRemoval(state,server,pos,moved);
    }

    public static final class Core extends IncubatorBlock implements EntityBlock {
        public Core(Properties properties) { super(properties); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            var level = context.getLevel();
            var pos = context.getClickedPos();
            if (!available(level, pos) || occupied(level, pos)) return null;
            for (int i = 0; i < 8; i++) {
                BlockPos p = cell(pos, i);
                var at = BlockPlaceContext.at(context, p, context.getClickedFace());
                var state = level.getBlockState(p);
                if (!state.canBeReplaced(at) || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || context.getPlayer() != null && !level.mayInteract(context.getPlayer(), p)) return null;
            }
            return defaultBlockState();
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!level.isClientSide()) install(level, pos, false);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Incubator(pos, state); }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide() || type != ModBlocks.INCUBATOR_ENTITY.get() ? null
                    : (world, pos, current, entity) -> Incubator.tick(world, pos, current, (Incubator) entity);
        }
    }

    public static final class Part extends IncubatorBlock {
        public Part(Properties properties) { super(properties); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(PART);
        }
    }
}

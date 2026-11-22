package yagen.waitmydawn.yagens_mobs.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class MailParcelBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(3, 0, 4, 13, 9, 12);

    public MailParcelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailParcelEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer user && level.getBlockEntity(pos) instanceof MailParcelEntity parcel) {
            ItemStack bag = parcel.bag();
            if (bag.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (user.getInventory().getFreeSlot() < 0) {
                user.displayClientMessage(Component.translatable("message.yagens_mobs.mail.inventory_full"), true);
                return InteractionResult.CONSUME;
            }
            if (level.removeBlock(pos, false)) {
                MailBag.refreshStationName(user.serverLevel(), bag);
                user.getInventory().placeItemBackInInventory(bag);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static @Nullable BlockPos land(ServerLevel level, BlockPos from, ItemStack bag, int radius) {
        int startY = Mth.clamp(from.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        for (BlockPos offset : BlockPos.spiralAround(from, radius, Direction.EAST, Direction.SOUTH)) {
            if (!level.hasChunkAt(offset) || !level.getWorldBorder().isWithinBounds(offset)) {
                continue;
            }
            var ground = new BlockPos.MutableBlockPos(offset.getX(), startY, offset.getZ());
            for (int y = startY; y >= level.getMinBuildHeight(); y--) {
                ground.setY(y);
                var state = level.getBlockState(ground);
                if (state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty()) {
                    continue;
                }
                BlockPos landing = ground.above();
                if (store(level, landing, bag)) {
                    return landing;
                }
                // A falling bag cannot pass through a floor to an empty room underneath it.
                break;
            }
        }
        return null;
    }

    private static boolean store(ServerLevel level, BlockPos pos, ItemStack bag) {
        if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).isAir()
                || !level.setBlockAndUpdate(pos, ModBlocks.MAIL_PARCEL.get().defaultBlockState())) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof MailParcelEntity parcel) {
            parcel.store(bag);
            return true;
        }
        level.removeBlock(pos, false);
        return false;
    }
}

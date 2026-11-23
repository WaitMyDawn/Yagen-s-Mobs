package yagen.waitmydawn.yagens_mobs.salvage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class SalvageNestBox extends BaseEntityBlock {
    public static final MapCodec<SalvageNestBox> CODEC = simpleCodec(SalvageNestBox::new);
    private static final String PATROL_PIN = "YagensSalvagePatrol";

    SalvageNestBox(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SalvageNestBoxEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box) {
            if (placer instanceof Player player) {
                box.setOwner(player.getUUID());
            }
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                box.nameChanged(stack.getHoverName());
            }
            box.ready(server);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box) {
            box.ready(level);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box) {
            if (player.isShiftKeyDown()) {
                message(server, box.toggleRescue(server.getUUID()) ? "rescue_on" : "rescue_off");
            } else {
                server.openMenu(box);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME) || stack.is(Items.COMPASS)
                || stack.is(Items.REDSTONE))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box
                && box.mayConfigure(server) && level.mayInteract(player, pos)) {
            if (stack.is(Items.NAME_TAG)) {
                String name = stack.getHoverName().getString();
                box.nameChanged(Component.literal(name.substring(0, Math.min(64, name.length()))));
                stack.consume(1, player);
                message(server, "renamed");
            } else if (stack.is(Items.COMPASS)) {
                configurePatrol(server, stack, box);
            } else {
                box.toggleGuard();
                message(server, box.guard() ? "guard_on" : "guard_off");
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void configurePatrol(ServerPlayer player, ItemStack compass, SalvageNestBoxEntity box) {
        ServerLevel level = player.serverLevel();
        var previous = compass.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(PATROL_PIN);
        String dimension = level.dimension().location().toString();
        if (previous.hasUUID("Id") && dimension.equals(previous.getString("Dimension"))) {
            var directory = SalvageNestDirectory.of(level);
            var endpoint = new SalvageNestDirectory.End(BlockPos.of(previous.getLong("Pos")), previous.getUUID("Id"));
            var source = directory.resolve(endpoint);
            if (source != null && !source.pos().equals(box.getBlockPos()) && source.mayConfigure(player)
                    && directory.connect(box.endpoint(), endpoint)) {
                CustomData.update(DataComponents.CUSTOM_DATA, compass, tag -> tag.remove(PATROL_PIN));
                message(player, "patrol_linked");
                return;
            }
        }
        CustomData.update(DataComponents.CUSTOM_DATA, compass, tag -> {
            var selection = new CompoundTag();
            selection.putString("Dimension", dimension);
            selection.putLong("Pos", box.getBlockPos().asLong());
            selection.putUUID("Id", box.id());
            tag.put(PATROL_PIN, selection);
        });
        message(player, "patrol_first");
    }

    static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.yagens_mobs.salvage." + key), true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (state.getBlock() != replacement.getBlock() && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box) {
            box.forget(server);
            Containers.dropContents(level, pos, box);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}

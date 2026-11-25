package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class CargoTongs extends Item {
    private static final String PAYLOAD = "VultureCargo";
    private static final String CORNER = "VultureCargoCorner";

    public CargoTongs(Properties settings) {
        super(settings);
    }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.getBlock() instanceof VulturePostBlock && state.getValue(VulturePostBlock.UPPER)) {
            return InteractionResult.PASS;
        }
        if (!loaded(stack) && !context.isSecondaryUseActive() && !state.hasBlockEntity()
                && !(state.getBlock() instanceof VulturePostBlock)
                && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return InteractionResult.PASS;
        }
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
            ItemStack held = context.getItemInHand();
            BlockPos point = context.getClickedPos();
            BlockState state = level.getBlockState(point);
            if (state.getBlock() instanceof VulturePostBlock && !state.getValue(VulturePostBlock.UPPER)) {
                var station = StationData.get(level).station(point);
                if (station != null && station.isOwnedBy(player)) {
                    onStation(level, player, held, station, point);
                }
                return InteractionResult.CONSUME;
            }
            if (loaded(held)) {
                Cargo cargo = read(level, held);
                if (cargo.size() > 0 && cargo.deploy(level, point.relative(context.getClickedFace()), player)) {
                    clear(held, PAYLOAD);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.yagens_mobs.tongs_blocked"));
                }
            } else if (player.isShiftKeyDown()) {
                select(level, player, held, point);
            } else {
                var cargo = Cargo.capture(level, player, point);
                if (cargo == null) {
                    return InteractionResult.PASS;
                }
                write(held, cargo);
            }
        }
        return (context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    void onStation(ServerLevel level, ServerPlayer player, ItemStack stack, StationData.Station station, BlockPos pos) {
        var data = StationData.get(level);
        if (loaded(stack) && !player.isShiftKeyDown()) {
            var load = read(level, stack);
            if (data.stage(level, station, pos, load)) {
                clear(stack, PAYLOAD);
            } else {
                player.sendOverlayMessage(Component.translatable("message.yagens_mobs.station_cannot_stage"));
            }
        } else if (!loaded(stack) && player.isShiftKeyDown()) {
            var cargo = data.cargo(pos);
            if (cargo != null) {
                write(stack, cargo);
                data.takeCargo(level, pos);
            }
        }
    }

    private static boolean loaded(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(PAYLOAD);
    }

    private static Cargo read(ServerLevel level, ItemStack stack) {
        return Cargo.load(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(PAYLOAD),
                level.registryAccess());
    }

    private static void select(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos point) {
        var mark = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(CORNER);
        String dimension = level.dimension().identifier().toString();
        if (!dimension.equals(mark.getStringOr("Dimension", "")) || mark.getLongOr("Pos", 0L) == point.asLong()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                var corner = new net.minecraft.nbt.CompoundTag();
                corner.putString("Dimension", dimension);
                corner.putLong("Pos", point.asLong());
                tag.put(CORNER, corner);
            });
            player.sendOverlayMessage(Component.translatable("message.yagens_mobs.tongs_corner",
                    point.getX(), point.getY(), point.getZ()));
            return;
        }
        var cargo = Cargo.captureRegion(level, player, BlockPos.of(mark.getLongOr("Pos", 0L)), point);
        clear(stack, CORNER);
        if (cargo != null) {
            write(stack, cargo);
        } else {
            player.sendOverlayMessage(Component.translatable("message.yagens_mobs.tongs_invalid_region"));
        }
    }

    private static void clear(ItemStack stack, String key) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(key);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static void write(ItemStack stack, Cargo cargo) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(CORNER);
            tag.put(PAYLOAD, cargo.save());
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> info, TooltipFlag flags) {
        if (loaded(stack)) {
            int count = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(PAYLOAD)
                    .getListOrEmpty("Parts").size();
            info.accept(Component.translatable("item.yagens_mobs.cargo_tongs.loaded", count));
        }
    }
}

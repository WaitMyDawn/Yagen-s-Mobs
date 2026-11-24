package yagen.waitmydawn.yagens_mobs.transport;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.compat.LegacyItemData;

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
                    player.displayClientMessage(Component.translatable("message.yagens_mobs.tongs_blocked"), true);
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
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    void onStation(ServerLevel level, ServerPlayer player, ItemStack stack, StationData.Station station, BlockPos pos) {
        var data = StationData.get(level);
        if (loaded(stack) && !player.isShiftKeyDown()) {
            var load = read(level, stack);
            if (data.stage(level, station, pos, load)) {
                clear(stack, PAYLOAD);
            } else {
                player.displayClientMessage(Component.translatable("message.yagens_mobs.station_cannot_stage"), true);
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
        return LegacyItemData.custom(stack).contains(PAYLOAD);
    }

    private static Cargo read(ServerLevel level, ItemStack stack) {
        return Cargo.load(LegacyItemData.custom(stack).getCompound(PAYLOAD),
                level.registryAccess());
    }

    private static void select(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos point) {
        var mark = LegacyItemData.custom(stack).getCompound(CORNER);
        String dimension = level.dimension().location().toString();
        if (!dimension.equals(mark.getString("Dimension")) || mark.getLong("Pos") == point.asLong()) {
            LegacyItemData.updateCustom(stack, tag -> {
                var corner = new net.minecraft.nbt.CompoundTag();
                corner.putString("Dimension", dimension);
                corner.putLong("Pos", point.asLong());
                tag.put(CORNER, corner);
            });
            player.displayClientMessage(Component.translatable("message.yagens_mobs.tongs_corner",
                    point.getX(), point.getY(), point.getZ()), true);
            return;
        }
        var cargo = Cargo.captureRegion(level, player, BlockPos.of(mark.getLong("Pos")), point);
        clear(stack, CORNER);
        if (cargo != null) {
            write(stack, cargo);
        } else {
            player.displayClientMessage(Component.translatable("message.yagens_mobs.tongs_invalid_region"), true);
        }
    }

    private static void clear(ItemStack stack, String key) {
        var tag = LegacyItemData.custom(stack);
        tag.remove(key);
        LegacyItemData.setCustom(stack, tag);
    }

    private static void write(ItemStack stack, Cargo cargo) {
        LegacyItemData.updateCustom(stack, tag -> {
            tag.remove(CORNER);
            tag.put(PAYLOAD, cargo.save());
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> info, TooltipFlag flags) {
        if (loaded(stack)) {
            int count = LegacyItemData.custom(stack).getCompound(PAYLOAD)
                    .getList("Parts", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            info.add(Component.translatable("item.yagens_mobs.cargo_tongs.loaded", count));
        }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.weasel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class SnowcacheFur extends Item {
    private static final String LABEL = "yagens_mobs:weasel_sort_label";
    private static final String MESSAGE = "message.yagens_mobs.snowcache_fur.";

    public SnowcacheFur(Properties properties) {
        super(properties);
    }

    public static void install() {
        WeaselChests.installPhysicalLabels(SnowcacheFur::readLabel, mob -> {
            if (mob.getRandom().nextInt(50) == 0) {
                mob.spawnAtLocation(ModItems.SNOWCACHE_FUR.get());
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(MESSAGE + "hint"));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var level = context.getLevel();
        var block = level.getBlockEntity(context.getClickedPos());
        if (context.getPlayer() == null || !(level.getBlockState(context.getClickedPos()).getBlock() instanceof ChestBlock)
                && (block == null || !NeoPhysicalStorage.supports(block))) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        var site = WeaselChests.at(server, context.getClickedPos(), player);
        if (site == null || site.routed()) {
            player.displayClientMessage(Component.translatable(MESSAGE + "unavailable"), true);
            return InteractionResult.CONSUME;
        }
        InteractionHand other = context.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(other);
        if (sample.isEmpty()) {
            if (player.isShiftKeyDown() && site.label() != null) {
                write(server, site, null);
                player.displayClientMessage(Component.translatable(MESSAGE + "cleared"), true);
            } else if (site.label() != null) {
                player.displayClientMessage(Component.translatable(MESSAGE + "set", description(site.label())), true);
            } else {
                player.displayClientMessage(Component.translatable(MESSAGE + "hint"), true);
            }
            return InteractionResult.CONSUME;
        }
        WeaselTabs.prepare(server);
        var label = player.isShiftKeyDown()
                ? new WeaselChests.Label(null, WeaselTabs.of(sample))
                : new WeaselChests.Label(sample.getItem(), null);
        if (!Objects.equals(site.label(), label)) {
            write(server, site, label);
            stack.consume(1, player);
        }
        player.displayClientMessage(Component.translatable(MESSAGE + "set", description(label)), true);
        return InteractionResult.CONSUME;
    }

    private static Component description(WeaselChests.Label label) {
        if (label.item() != null) return label.item().getDescription();
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(label.tab());
        return tab == null ? Component.translatable(MESSAGE + "uncategorized") : tab.getDisplayName();
    }

    private static @Nullable WeaselChests.Label readLabel(ServerLevel level, WeaselChests.Site site) {
        BlockEntity entity = level.getBlockEntity(site.pos());
        if (entity == null) return null;
        ItemStack backpack = NeoPhysicalStorage.backpackStack(entity);
        if (!backpack.isEmpty()) {
            return decode(backpack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(LABEL));
        }
        var data = entity.getPersistentData();
        if (!data.contains(LABEL)) {
            BlockEntity other = otherHalf(level, site.pos());
            if (other != null) data = other.getPersistentData();
        }
        return decode(data.getCompound(LABEL));
    }

    private static @Nullable WeaselChests.Label decode(CompoundTag tag) {
        String itemName = tag.getString("Item");
        if (!itemName.isEmpty()) {
            var id = ResourceLocation.tryParse(itemName);
            return id == null ? null : BuiltInRegistries.ITEM.getOptional(id)
                    .map(item -> new WeaselChests.Label(item, null)).orElse(null);
        }
        String tabName = tag.getString("Tab");
        var tab = tabName.isEmpty() ? null : ResourceLocation.tryParse(tabName);
        return tab == null ? null : new WeaselChests.Label(null, tab);
    }

    private static void write(ServerLevel level, WeaselChests.Site site, @Nullable WeaselChests.Label label) {
        BlockEntity entity = level.getBlockEntity(site.pos());
        if (entity == null) return;
        CompoundTag encoded = new CompoundTag();
        if (label != null) {
            if (label.item() != null) encoded.putString("Item", BuiltInRegistries.ITEM.getKey(label.item()).toString());
            else encoded.putString("Tab", label.tab().toString());
        }
        ItemStack backpack = NeoPhysicalStorage.backpackStack(entity);
        if (!backpack.isEmpty()) {
            // The wrapped stack follows a backpack when it is picked up and placed elsewhere.
            CustomData.update(DataComponents.CUSTOM_DATA, backpack, tag -> setLabel(tag, encoded));
            entity.setChanged();
            return;
        }
        setLabel(entity.getPersistentData(), encoded);
        entity.setChanged();
        BlockEntity other = otherHalf(level, site.pos());
        if (other != null) {
            setLabel(other.getPersistentData(), encoded);
            other.setChanged();
        }
    }

    private static void setLabel(CompoundTag data, CompoundTag encoded) {
        if (encoded.isEmpty()) data.remove(LABEL);
        else data.put(LABEL, encoded.copy());
    }

    private static @Nullable BlockEntity otherHalf(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return level.getBlockEntity(pos.relative(ChestBlock.getConnectedDirection(state)));
        }
        return null;
    }
}

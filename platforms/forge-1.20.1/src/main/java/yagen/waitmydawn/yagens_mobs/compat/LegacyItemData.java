package yagen.waitmydawn.yagens_mobs.compat;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class LegacyItemData {
    private static final String CUSTOM = "YagensMobs";
    private static final String INVENTORY = "YagensMobsInventory";
    private static final String BLOCK_STATE = "BlockStateTag";

    private LegacyItemData() {}

    public static void consume(ItemStack stack, int count, net.minecraft.world.entity.player.Player player) {
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(count);
        }
    }

    public static ItemStack take(ItemStack stack, int count, net.minecraft.world.entity.player.Player player) {
        ItemStack taken = stack.copyWithCount(Math.min(count, stack.getCount()));
        consume(stack, count, player);
        return taken;
    }

    public static CompoundTag root(ItemStack stack) {
        return stack.getTag() == null ? new CompoundTag() : stack.getTag();
    }

    public static CompoundTag custom(ItemStack stack) {
        return root(stack).getCompound(CUSTOM).copy();
    }

    public static void setCustom(ItemStack stack, CompoundTag contents) {
        if (!contents.isEmpty()) {
            stack.addTagElement(CUSTOM, contents.copy());
        } else if (stack.getTag() != null) {
            stack.removeTagKey(CUSTOM);
        }
    }

    public static void updateCustom(ItemStack stack, Consumer<CompoundTag> update) {
        var contents = custom(stack);
        update.accept(contents);
        setCustom(stack, contents);
    }

    public static void loadItems(ItemStack stack, NonNullList<ItemStack> contents) {
        ContainerHelper.loadAllItems(root(stack).getCompound(INVENTORY), contents);
    }

    public static void saveItems(ItemStack stack, NonNullList<ItemStack> contents) {
        stack.addTagElement(INVENTORY, ContainerHelper.saveAllItems(new CompoundTag(), contents));
    }

    public static List<ItemStack> items(ItemStack stack, int slots) {
        var contents = NonNullList.withSize(slots, ItemStack.EMPTY);
        loadItems(stack, contents);
        return contents.stream().filter(item -> !item.isEmpty()).toList();
    }

    public static BlockState applyProperties(ItemStack stack, BlockState state) {
        var properties = root(stack).getCompound(BLOCK_STATE);
        for (Property<?> property : state.getProperties()) {
            if (properties.contains(property.getName())) {
                state = applyProperty(state, property, properties.getString(property.getName()));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    public static void saveProperties(ItemStack stack, BlockState state) {
        var properties = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            properties.putString(property.getName(), propertyValue(state, property));
        }
        stack.addTagElement(BLOCK_STATE, properties);
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}

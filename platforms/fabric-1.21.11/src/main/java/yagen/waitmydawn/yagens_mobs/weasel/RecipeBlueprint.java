package yagen.waitmydawn.yagens_mobs.weasel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class RecipeBlueprint extends Item {
    private static final String PLAN = "WeaselBlueprint";

    RecipeBlueprint(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable BlueprintPlan read(ItemStack item) {
        return BlueprintPlan.load(item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(PLAN));
    }

    static void write(ItemStack item, BlueprintPlan plan) {
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.put(PLAN, plan.save()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!SnowcacheWeasels.blueprintsEnabled()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner) {
            int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
            WeaselPackets.open(owner, slot);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> text, TooltipFlag flag) {
        var plan = read(stack);
        if (plan != null) {
            var icon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(plan.output()));
            text.accept(Component.translatable("item.yagens_mobs.recipe_blueprint.goal", plan.quantity(), icon.getHoverName()));
        }
    }
}


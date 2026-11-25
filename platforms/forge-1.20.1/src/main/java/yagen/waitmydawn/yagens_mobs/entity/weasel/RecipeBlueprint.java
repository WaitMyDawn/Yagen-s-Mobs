package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class RecipeBlueprint extends Item {
    private static final String PLAN = "WeaselBlueprint";

    RecipeBlueprint() {
        super(new Properties().stacksTo(1));
    }

    public static @Nullable BlueprintPlan read(ItemStack item) {
        var tag = item.getTagElement(PLAN);
        return tag == null ? null : BlueprintPlan.load(tag);
    }

    static void write(ItemStack item, BlueprintPlan plan) {
        item.addTagElement(PLAN, plan.save());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SnowcacheWeasels.blueprintsEnabled()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer owner) {
            int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
            WeaselPackets.open(owner, slot);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> text, TooltipFlag flag) {
        var plan = read(stack);
        if (plan != null) {
            var icon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(plan.output()));
            text.add(Component.translatable("item.yagens_mobs.recipe_blueprint.goal", plan.quantity(), icon.getHoverName()));
        }
    }
}


package yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class LauncherMenu extends AbstractContainerMenu {
    public static final int SIZE = 5;
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModIdentity.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<LauncherMenu>> TYPE = MENUS.register("silk_launcher",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new LauncherMenu(id, inventory,
                    InteractionHand.MAIN_HAND, ItemStack.EMPTY)));

    private final ItemStack launcher;
    private final InteractionHand hand;
    private final DataSlot lockedSlot = DataSlot.standalone();

    LauncherMenu(int id, Inventory inventory, InteractionHand hand, ItemStack launcher) {
        super(TYPE.get(), id);
        this.launcher = launcher;
        this.hand = hand;
        lockedSlot.set(launcher.isEmpty() ? -1 : hand == InteractionHand.MAIN_HAND
                ? inventory.selected : Inventory.SLOT_OFFHAND);
        addDataSlot(lockedSlot);
        var chamber = new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!launcher.isEmpty()) {
                    launcher.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
                    inventory.setChanged();
                }
            }
        };
        launcher.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(chamber.getItems());
        for (int slot = 0; slot < SIZE; slot++) {
            addSlot(new Slot(chamber, slot, 44 + slot * 18, 26) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.ROCK_SILK_SPOOL.get());
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 65 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 123) {
                @Override
                public boolean mayPickup(Player player) {
                    return getContainerSlot() != lockedSlot.get();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return getContainerSlot() != lockedSlot.get();
                }
            });
        }
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    @Override
    public boolean stillValid(Player player) {
        return launcher.isEmpty() || player.isAlive() && player.getItemInHand(hand) == launcher;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player) && !(type == ClickType.SWAP && button == lockedSlot.get())) {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!(index < SIZE ? moveItemStackTo(stack, SIZE, slots.size(), true)
                : moveItemStackTo(stack, 0, SIZE, false))) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }
}


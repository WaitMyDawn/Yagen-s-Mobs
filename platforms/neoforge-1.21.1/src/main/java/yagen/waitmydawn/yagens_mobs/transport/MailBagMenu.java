package yagen.waitmydawn.yagens_mobs.transport;

import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class MailBagMenu extends AbstractContainerMenu {
    public static final int SEND = 0;
    private static final int SIZE = 27;
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModIdentity.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<MailBagMenu>> TYPE = MENUS.register("mail_bag",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new MailBagMenu(id, inventory, null, null)));

    private final ItemStack bag;
    private final InteractionHand hand;
    private final DataSlot handSlot = DataSlot.standalone();
    private final DataSlot ready = DataSlot.standalone();
    @Nullable private final ServerPlayer holder;
    @Nullable private String lastSentName;
    @Nullable private Component boundLabel;
    private boolean dispatched;
    private long nextAttempt;

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    MailBagMenu(int id, Inventory inventory, InteractionHand hand, ItemStack bag) {
        super(TYPE.get(), id);
        this.bag = bag;
        this.hand = hand;
        holder = inventory.player instanceof ServerPlayer player ? player : null;
        int selected = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : inventory.selected;
        handSlot.set(bag == null ? -1 : selected);
        var contents = new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (bag != null && !dispatched) {
                    bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
                    inventory.setChanged();
                }
            }
        };
        if (bag != null) {
            bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents.getItems());
            ready.set(MailBag.ready(bag) ? 1 : 0);
        }
        addDataSlot(ready);
        addDataSlot(handSlot);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(contents, row * 9 + column, 8 + 18 * column, 18 + 18 * row) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return MailBag.canStore(stack);
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + 18 * column, 85 + 18 * row));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + 18 * column, 143) {
                @Override
                public boolean mayPickup(Player player) {
                    return getContainerSlot() != handSlot.get();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return getContainerSlot() != handSlot.get();
                }
            });
        }
    }

    public boolean canSend() {
        return ready.get() != 0;
    }

    public @Nullable Component boundLabel() {
        return boundLabel;
    }

    void updateBoundLabel(String name) {
        boundLabel = MailBag.stationLabel(name);
    }

    @Override
    public void broadcastChanges() {
        if (holder != null && stillValid(holder)) {
            MailBag.refreshStationName(holder.serverLevel(), bag);
            String name = MailBag.cachedStationName(bag);
            if (name != null && !Objects.equals(lastSentName, name)) {
                lastSentName = name;
                PacketDistributor.sendToPlayer(holder, new StationPackets.MailName(containerId, name));
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return bag == null || !dispatched && player.isAlive() && player.getItemInHand(hand) == bag
                && bag.is(ModItems.MAIL_BAG.get());
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player) && !(type == ClickType.SWAP && button == handSlot.get())) {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != SEND || !canSend() || !stillValid(player) || !(player instanceof ServerPlayer sender)) {
            return false;
        }
        long now = sender.serverLevel().getGameTime();
        if (now < nextAttempt) {
            return false;
        }
        nextAttempt = now + 20;
        if (!MailBag.dispatch(sender, bag)) {
            return false;
        }
        dispatched = true;
        player.setItemInHand(hand, ItemStack.EMPTY);
        sender.closeContainer();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        var stack = slot.getItem();
        var original = stack.copy();
        if (!(index < SIZE ? moveItemStackTo(stack, SIZE, slots.size(), true)
                : MailBag.canStore(stack) && moveItemStackTo(stack, 0, SIZE, false))) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }
}

package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class IncubatorMenu extends AbstractContainerMenu {
    static final int START = 0, ARCHIVE = 1, BACK = 2, PREVIOUS = 3, NEXT = 4, REVIVE = 5, DELETE = 6;
    private final IncubatorSamples samples;
    private final Incubator machine;
    private final ServerPlayer owner;
    final DataSlot remainingSeconds = DataSlot.standalone();
    boolean archive, confirming;
    int page, count, revision;
    CompoundTag detail = new CompoundTag();
    private String selected;

    public IncubatorMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    IncubatorMenu(int id, Inventory inv, Incubator machine) {
        super(ModBlocks.INCUBATOR_MENU.get(), id);
        this.machine = machine;
        this.owner = inv.player instanceof ServerPlayer player ? player : null;
        samples = machine == null ? new IncubatorSamples() : machine.samples;
        remainingSeconds.set(-1);
        for (int i = 0; i < 10; i++) {
            final int index = i;
            addSlot(new Slot(samples, i, 12 + i % 5 * 18, 36 + i / 5 * 18) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean isActive() { return !archive && samples.visibleSample(index); }
                @Override public boolean mayPlace(ItemStack stack) { return !archive && samples.canPlaceItem(index, stack); }
            });
        }
        for (int i = 0; i < 36; i++) {
            int index = i < 27 ? i + 9 : i - 27;
            addSlot(new Slot(inv, index, 32 + i % 9 * 18, i < 27 ? 104 + i / 9 * 18 : 162) {
                @Override public boolean isActive() { return !archive; }
            });
        }
        addDataSlot(remainingSeconds);
    }

    boolean ready() { return samples.ready(); }

    @Override public boolean stillValid(Player player) {
        return machine == null ? player.level().isClientSide
                : player == owner && !machine.isRemoved() && player.level() == machine.getLevel()
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (archive || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < 10) {
            if (!moveItemStackTo(stack, 10, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            for (int i = 0; i < 10 && !stack.isEmpty(); i++) {
                var input = getSlot(i);
                if (!input.hasItem() && input.mayPlace(stack)) input.set(stack.split(1));
            }
        }
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (archive || slot >= slots.size() || slot >= 0 && !getSlot(slot).isActive()) return;
        super.clicked(slot, button, type, player);
    }

    @Override public void broadcastChanges() {
        if (machine != null) {
            remainingSeconds.set(machine.busy() ? machine.seconds() : -1);
            if (archive && owner.tickCount % 20 == 0) {
                var records = KavatMemories.memories(owner);
                if (count != records.size() || selected != null && !records.contains(selected)) showPage(0);
            }
        }
        super.broadcastChanges();
    }

    private void showPage(int direction) {
        var memories = KavatMemories.memories(owner);
        var keys = memories.getAllKeys().stream().sorted().toList();
        count = keys.size();
        int previous = selected == null ? -1 : keys.indexOf(selected);
        page = net.minecraft.util.Mth.clamp((previous < 0 ? page : previous) + direction, 0, Math.max(0, count - 1));
        selected = count == 0 ? null : keys.get(page);
        confirming = false;
        detail = new CompoundTag();
        if (selected != null) {
            var source = memories.getCompound(selected);
            for (String key : List.of("CustomName", "KavatTraits", "Generation", "TemplatesMade"))
                if (source.contains(key)) detail.put(key, source.get(key).copy());
        }
        sendView();
    }

    private void sendView() {
        // A button belongs to one rendered record and confirmation state, even if the page later changes.
        revision = revision % 0x0fffffff + 1;
        var view = detail.copy();
        view.putBoolean("Archive", archive);
        view.putBoolean("Confirm", confirming);
        view.putInt("Page", page);
        view.putInt("Count", count);
        view.putInt("Revision", revision);
        LegacyNetwork.sendToPlayer(owner, new IncubatorPage(containerId, view));
    }

    public void receive(CompoundTag view) {
        detail = view;
        archive = view.getBoolean("Archive");
        confirming = view.getBoolean("Confirm");
        page = view.getInt("Page");
        count = view.getInt("Count");
        revision = view.getInt("Revision");
    }

    int action(int operation) { return (revision << 3) | operation; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player) || !getCarried().isEmpty() || (button >>> 3) != revision) return false;
        int op = button & 7;
        if (!archive) {
            if (op == START) machine.breed(owner);
            else if (op == ARCHIVE) {
                archive = true;
                showPage(0);
            } else return false;
        } else if (op == BACK) {
            archive = false;
            confirming = false;
            sendView();
        } else if (op == PREVIOUS || op == NEXT) showPage(op == NEXT ? 1 : -1);
        else if (selected != null && KavatMemories.memories(owner).contains(selected)) {
            if (op == REVIVE && !machine.busy()) {
                machine.revive(owner, selected);
                if (machine.busy()) { archive = false; sendView(); }
            } else if (op == DELETE) {
                if (confirming) {
                    KavatMemories.memories(owner).remove(selected);
                    showPage(0);
                } else {
                    confirming = true;
                    sendView();
                }
            } else return false;
        } else return false;
        broadcastChanges();
        return true;
    }
}

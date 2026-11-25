package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

/** The top rows are previews. Materials stay in the player's inventory until the server commits a job. */
public final class IncubatorMenu extends AbstractContainerMenu {
    private final SimpleContainer box = new SimpleContainer(36);
    static final int WIDTH = 230, HEIGHT = 221;
    public SimpleContainer getContainer() {
        return box;
    }
    private final Incubator machine;
    private final ServerPlayer owner;
    private final List<Integer> selected = new ArrayList<>();
    private final Map<Integer, ItemStack> expected = new HashMap<>();
    private List<Kavat> pets = List.of();
    private List<String> records = List.of();
    private int page;
    private boolean archive, confirm;
    private String chosen;
    final DataSlot remainingSeconds = DataSlot.standalone();
    public IncubatorMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }
    IncubatorMenu(int id, Inventory inv, Incubator machine) {
        super(yagen.waitmydawn.yagens_mobs.registries.ModBlocks.INCUBATOR_MENU.get(), id);
        this.machine = machine;
        owner = inv.player instanceof ServerPlayer p ? p : null;
        for (int i = 0; i < 36; i++) {
            int x = -1000, y = -1000;
            if (i < 10) {
                x = 10 + i % 5 * 18;
                y = 29 + i / 5 * 18;
            } else if (i >= 27) {
                x = 10 + (i - 27) * 18;
                y = 103;
            } else {
                switch (i) {
                    case 11 -> {
                        x = 109;
                        y = 29;
                    }
                    case 13 -> {
                        x = 109;
                        y = 49;
                    }
                    case 18 -> {
                        x = 10;
                        y = 78;
                    }
                    case 19 -> {
                        x = 108;
                        y = 78;
                    }
                    case 21 -> {
                        x = 178;
                        y = 49;
                    }
                    case 22 -> {
                        x = 200;
                        y = 49;
                    }
                    case 24 -> {
                        x = 178;
                        y = 103;
                    }
                    case 26 -> {
                        x = 200;
                        y = 103;
                    }
                }
            }
            addSlot(new Slot(box, i, x, y));
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, 34 + 18 * col, 135 + 18 * row));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 34 + 18 * col, 193));
        addDataSlot(remainingSeconds);
        if (machine != null)
            refresh();
    }
    @Override
    public boolean stillValid(Player player) {
        return machine == null ? player.level().isClientSide
                               : player == owner && !machine.isRemoved() && player.level() == machine.getLevel()
                        && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
    private void icon(int slot, Item item, String key, Object... args) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("kavat." + key, args));
        getContainer().setItem(slot, stack);
    }
    private void refresh() {
        var box = getContainer();
        box.clearContent();
        remainingSeconds.set(machine.busy() ? machine.seconds() : -1);
        selected.removeIf(i -> !ItemStack.isSameItemSameComponents(expected.get(i), owner.getInventory().getItem(i)));
        for (int i = 0; i < selected.size(); i++)
            box.setItem(i, owner.getInventory().getItem(selected.get(i)).copyWithCount(1));
        icon(11, Items.PAPER, "choose_samples");
        icon(13, Items.CLOCK, machine.busy() ? "progress" : "start", machine.seconds());
        icon(18, Items.SHEARS, "pets");
        icon(19, Items.RECOVERY_COMPASS, "memories");
        box.getItem(archive ? 19 : 18).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        pets = machine.pets(owner);
        var memory = KavatMemories.memories(owner);
        records = memory.getAllKeys().stream().sorted().toList();
        int n = archive ? records.size() : pets.size();
        page = Math.clamp(page, 0, Math.max(0, (n - 1) / 9));
        if (page > 0)
            icon(24, Items.ARROW, "previous");
        if ((page + 1) * 9 < n)
            icon(26, Items.ARROW, "next");
        for (int i = 0; i < 9 && page * 9 + i < n; i++) {
            int index = page * 9 + i;
            if (archive) {
                String key = records.get(index);
                var tag = memory.getCompound(key);
                var genes = KavatGenes.unpack(tag.getInt("KavatTraits"));
                var material = KavatSample.MATERIALS.get(genes.coat());
                icon(27 + i, material, "memory_entry", name(tag),
                        KavatSample.trait(KavatGenes.BREEDS.get(genes.breed())));
                box.getItem(27 + i).set(DataComponents.LORE,
                        new ItemLore(List.of(new KavatSample(genes, 0, null).description(), Component.literal(key),
                                Component.translatable("kavat.cost", material.getDescription()))));
            } else {
                Kavat k = pets.get(index);
                icon(27 + i, ModItems.KAVAT_TEMPLATE.get(), "slice_entry", k.getDisplayName(), k.remaining());
                box.getItem(27 + i).set(DataComponents.LORE,
                        new ItemLore(List.of(k.sample().description(), Component.literal(k.getStringUUID()))));
            }
        }
        if (chosen != null && memory.contains(chosen)) {
            icon(21, Items.TOTEM_OF_UNDYING, "revive", name(memory.getCompound(chosen)));
            icon(22, Items.LAVA_BUCKET, confirm ? "confirm_delete" : "delete", name(memory.getCompound(chosen)));
        }
    }
    private Component name(CompoundTag tag) {
        if (tag.contains("CustomName")) {
            var name = Component.Serializer.fromJson(tag.getString("CustomName"), owner.registryAccess());
            if (name != null)
                return name;
        }
        return Component.translatable("entity.yagens_mobs.kavat");
    }
    @Override
    public void broadcastChanges() {
        if (machine != null && owner.tickCount % 20 == 0)
            refresh();
        super.broadcastChanges();
    }
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (machine == null || !stillValid(player))
            return;
        if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && slot >= 0 && slot < slots.size()) {
            if (slot >= 36) {
                int inv = getSlot(slot).getContainerSlot();
                ItemStack s = owner.getInventory().getItem(inv);
                if ((s.is(ModItems.KAVAT_CODE.get()) || s.is(ModItems.KAVAT_TEMPLATE.get()))
                        && KavatSample.read(s) != null) {
                    int cap = s.is(ModItems.KAVAT_CODE.get()) ? 10 : 2;
                    if (selected.isEmpty() || expected.get(selected.getFirst()).is(s.getItem())) {
                        int room = Math.min(cap - selected.size(), s.getCount() - Collections.frequency(selected, inv));
                        int add = type == ClickType.QUICK_MOVE ? room : Math.min(1, room);
                        for (int i = 0; i < add; i++) selected.add(inv);
                        expected.put(inv, s.copyWithCount(1));
                    }
                }
            } else if (slot < selected.size())
                selected.remove(slot);
            else if (slot == 13 && !machine.busy()) {
                if (selected.stream().allMatch(
                            i -> ItemStack.isSameItemSameComponents(expected.get(i), owner.getInventory().getItem(i))))
                    machine.breed(owner, selected);
                if (machine.busy())
                    selected.clear();
            } else if (slot == 18 || slot == 19) {
                archive = slot == 19;
                page = 0;
                chosen = null;
                confirm = false;
            } else if (slot == 24 || slot == 26) {
                page += slot == 24 ? -1 : 1;
                chosen = null;
                confirm = false;
            } else if (slot >= 27) {
                int i = page * 9 + slot - 27;
                if (archive && i < records.size()) {
                    chosen = records.get(i);
                    confirm = false;
                }
                if (!archive && i < pets.size() && !machine.busy()) {
                    Kavat cat = pets.get(i);
                    if (machine.pets(owner).contains(cat)) {
                        var stack = cat.slice();
                        if (!stack.isEmpty())
                            Kavat.give(owner, stack);
                    }
                }
            } else if (slot == 21 && chosen != null) {
                machine.revive(owner, chosen);
                confirm = false;
            } else if (slot == 22 && chosen != null && !machine.busy()) {
                if (confirm) {
                    KavatMemories.memories(owner).remove(chosen);
                    chosen = null;
                }
                confirm = !confirm;
            }
        }
        refresh();
        sendAllDataToRemote();
    }
}

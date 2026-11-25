package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.flag.FeatureFlags;

final class SalvageNestMenu extends ChestMenu {
    private static final MenuType<SalvageNestMenu> MENU = Registry.register(BuiltInRegistries.MENU,
            SalvageRays.id("salvage_nest"), new MenuType<>((id, inventory) -> new SalvageNestMenu(id, inventory,
                    new SimpleContainer(27), null, null, false), FeatureFlags.DEFAULT_FLAGS));
    static final Supplier<MenuType<SalvageNestMenu>> TYPE = () -> MENU;

    final @Nullable SalvageNestDirectory.End box;
    private final DataSlot renameAllowed = DataSlot.standalone();

    SalvageNestMenu(int id, Inventory inventory, Container container, @Nullable BlockPos pos, @Nullable UUID uuid,
                    boolean canRename) {
        super(TYPE.get(), id, inventory, container, 3);
        this.box = pos == null || uuid == null ? null : new SalvageNestDirectory.End(pos, uuid);
        renameAllowed.set(canRename ? 1 : 0);
        addDataSlot(renameAllowed);
    }

    boolean canRename() {
        return renameAllowed.get() == 1;
    }
}

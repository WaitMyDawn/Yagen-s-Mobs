package yagen.waitmydawn.yagens_mobs.entity.salvage;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

final class SalvageNestMenu extends ChestMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModIdentity.ID);
    static final DeferredHolder<MenuType<?>, MenuType<SalvageNestMenu>> TYPE = MENUS.register("salvage_nest",
            () -> IMenuTypeExtension.create((id, inventory, buffer) -> new SalvageNestMenu(id, inventory,
                    new SimpleContainer(27), buffer.readBlockPos(), buffer.readUUID(), buffer.readBoolean())));

    final SalvageNestDirectory.End box;
    final boolean canRename;

    SalvageNestMenu(int id, Inventory inventory, Container container, BlockPos pos, UUID uuid, boolean canRename) {
        super(TYPE.get(), id, inventory, container, 3);
        this.box = new SalvageNestDirectory.End(pos, uuid);
        this.canRename = canRename;
    }

    static void register(IEventBus bus) { MENUS.register(bus); }
}

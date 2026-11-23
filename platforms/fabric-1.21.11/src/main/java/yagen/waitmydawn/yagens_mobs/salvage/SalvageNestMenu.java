package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

final class SalvageNestMenu extends ChestMenu {
    record Opening(BlockPos pos, UUID id, boolean canRename) {
        static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Opening::pos, UUIDUtil.STREAM_CODEC, Opening::id,
                ByteBufCodecs.BOOL, Opening::canRename, Opening::new);
    }

    private static final MenuType<SalvageNestMenu> MENU = Registry.register(BuiltInRegistries.MENU,
            SalvageRays.id("salvage_nest"), new ExtendedScreenHandlerType<>(
                    (int id, Inventory inventory, Opening opening) -> new SalvageNestMenu(id, inventory,
                            new SimpleContainer(27), opening.pos(), opening.id(), opening.canRename()), Opening.CODEC));
    static final Supplier<MenuType<SalvageNestMenu>> TYPE = () -> MENU;

    final SalvageNestDirectory.End box;
    final boolean canRename;

    SalvageNestMenu(int id, Inventory inventory, Container container, BlockPos pos, UUID uuid, boolean canRename) {
        super(TYPE.get(), id, inventory, container, 3);
        this.box = new SalvageNestDirectory.End(pos, uuid);
        this.canRename = canRename;
    }

    static void register() {}
}

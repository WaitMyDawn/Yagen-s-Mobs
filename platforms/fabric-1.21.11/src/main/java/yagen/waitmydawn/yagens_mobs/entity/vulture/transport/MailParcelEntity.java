package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

public final class MailParcelEntity extends BlockEntity {
    private ItemStack bag = ItemStack.EMPTY;

    public MailParcelEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAIL_PARCEL_ENTITY.get(), pos, state);
    }

    ItemStack bag() {
        return bag.copy();
    }

    void store(ItemStack payload) {
        bag = payload.copy();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var tag = new CompoundTag();
        var registries = level.registryAccess();
        if (!bag.isEmpty()) {
            tag.put("Bag", SaveTag.saveItem(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), bag));
        }
        SaveTag.write(output, tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        var tag = SaveTag.read(input);
        var registries = input.lookup();
        var stack = ItemStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.getCompoundOrEmpty("Bag")).result().orElse(ItemStack.EMPTY);
        bag = stack.is(ModItems.MAIL_BAG.get()) ? stack : ItemStack.EMPTY;
    }
}

package yagen.waitmydawn.yagens_mobs.transport;

import net.minecraft.core.BlockPos;
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!bag.isEmpty()) {
            tag.put("Bag", bag.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var stack = ItemStack.of(tag.getCompound("Bag"));
        bag = stack.is(ModItems.MAIL_BAG.get()) ? stack : ItemStack.EMPTY;
    }
}

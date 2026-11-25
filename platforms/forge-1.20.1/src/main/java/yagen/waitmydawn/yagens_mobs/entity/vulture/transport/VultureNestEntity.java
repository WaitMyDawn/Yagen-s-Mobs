package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class VultureNestEntity extends BlockEntity {
    private static final int INCUBATION_TICKS = 5 * 60 * 20;
    private long hatchAt;

    public VultureNestEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.VULTURE_NEST_ENTITY.get(), pos, state);
    }

    public void beginIncubation(ServerLevel level) {
        hatchAt = level.getGameTime() + INCUBATION_TICKS;
        setChanged();
    }

    static void tick(Level level, BlockPos pos, BlockState state, VultureNestEntity nest) {
        ServerLevel server = (ServerLevel) level;
        if (nest.hatchAt == 0L) {
            nest.beginIncubation(server);
            return;
        }
        if (level.getGameTime() < nest.hatchAt || level.getGameTime() % 20 != 0) {
            return;
        }
        int remaining = state.getValue(VultureNestBlock.EGGS);
        while (remaining > 0) {
            var chick = ModEntities.VULTURE.get().create(level);
            if (chick == null) {
                break;
            }
            chick.setBaby(true);
            chick.setPersistenceRequired();
            Vec3 position = Vec3.atBottomCenterOf(pos).add(0.0, 0.32, 0.0);
            chick.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360.0F, 0.0F);
            if (!server.addFreshEntity(chick)) {
                break;
            }
            remaining--;
        }
        if (remaining == 0) {
            level.removeBlock(pos, false);
            level.levelEvent(2001, pos, Block.getId(state));
            Block.popResource(level, pos, new ItemStack(Items.STICK, 2 + level.random.nextInt(3)));
        } else if (remaining != state.getValue(VultureNestBlock.EGGS)) {
            level.setBlock(pos, state.setValue(VultureNestBlock.EGGS, remaining), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("HatchAt", hatchAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hatchAt = tag.getLong("HatchAt");
    }
}

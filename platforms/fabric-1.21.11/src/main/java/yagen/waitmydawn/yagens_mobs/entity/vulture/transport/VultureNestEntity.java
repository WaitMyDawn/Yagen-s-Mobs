package yagen.waitmydawn.yagens_mobs.entity.vulture.transport;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.yagens_mobs.registries.CreatureCue;
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
            var chick = ModEntities.VULTURE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
            if (chick == null) {
                break;
            }
            chick.setBaby(true);
            chick.setPersistenceRequired();
            Vec3 position = Vec3.atBottomCenterOf(pos).add(0.0, 0.32, 0.0);
            chick.snapTo(position.x, position.y, position.z, level.random.nextFloat() * 360.0F, 0.0F);
            if (!server.addFreshEntity(chick)) {
                break;
            }
            remaining--;
        }
        if (remaining == 0) {
            level.removeBlock(pos, false);
            var hatch = CreatureCue.CONDROC_HATCH.orElse(null);
            if (hatch == null) {
                level.levelEvent(2001, pos, Block.getId(state));
            } else {
                level.playSound(null, pos, hatch, SoundSource.BLOCKS, .8F, 1F);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + .5,
                        pos.getY() + .15, pos.getZ() + .5, 20, .33, .08, .33, .035);
            }
            Block.popResource(level, pos, new ItemStack(Items.STICK, 2 + level.random.nextInt(3)));
        } else if (remaining != state.getValue(VultureNestBlock.EGGS)) {
            level.setBlock(pos, state.setValue(VultureNestBlock.EGGS, remaining), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var tag = new CompoundTag();
        tag.putLong("HatchAt", hatchAt);
        SaveTag.write(output, tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        var tag = SaveTag.read(input);
        hatchAt = tag.getLongOr("HatchAt", 0L);
    }
}

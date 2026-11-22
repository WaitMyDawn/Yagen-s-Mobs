package yagen.waitmydawn.yagens_mobs.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;

public final class VulturePostEntity extends BlockEntity {
    @Nullable private BlockState displayedCargo;

    public VulturePostEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.VULTURE_POST_ENTITY.get(), pos, state);
    }

    public @Nullable BlockState displayedCargo() {
        return displayedCargo;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (level instanceof ServerLevel server) {
            var state = StationData.get(server).displayCargo(worldPosition);
            // NeoForge skips empty block entity data packets, including when an occupied slot is cleared.
            tag.put("Display", NbtUtils.writeBlockState(state == null ? Blocks.AIR.defaultBlockState() : state));
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayedCargo = tag.contains("Display")
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Display")) : null;
        if (displayedCargo != null && displayedCargo.isAir()) {
            displayedCargo = null;
        }
    }
}

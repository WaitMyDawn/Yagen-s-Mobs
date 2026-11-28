package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import net.minecraft.world.level.block.Block;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderBlock;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

final class ConnectedBladderModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockState> CONNECTED = new ModelProperty<>();

    ConnectedBladderModel(BakedModel model, Block block) {
        super(model);
        // Chunk mesh workers share this multipart model. Populate its non-concurrent
        // selector cache before any worker requests a virtual connection state.
        if (model instanceof MultiPartBakedModel multipart) {
            block.getStateDefinition().getPossibleStates().forEach(multipart::getSelectors);
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        // Old saves and structure placements can lack the directional properties.
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),
                    AirBladderBlock.connects(level.getBlockState(pos.relative(side))));
        }
        return super.getModelData(level, pos, state, data).derive().with(CONNECTED, state).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        BlockState connected = data.get(CONNECTED);
        return super.getQuads(connected == null ? state : connected, side, random, data, layer);
    }
}

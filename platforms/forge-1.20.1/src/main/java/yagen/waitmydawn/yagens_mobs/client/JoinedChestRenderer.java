package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

final class JoinedChestRenderer {
    private final ModelPart left;
    private final ModelPart right;
    private final boolean holiday;
    private final Map<Block, BlockEntity> materials = new HashMap<>();

    JoinedChestRenderer(Function<ModelLayerLocation, ModelPart> bakeLayer) {
        left = bakeLayer.apply(ModelLayers.DOUBLE_CHEST_LEFT);
        right = bakeLayer.apply(ModelLayers.DOUBLE_CHEST_RIGHT);
        LocalDate date = LocalDate.now();
        holiday = date.getMonthValue() == 12 && date.getDayOfMonth() >= 24 && date.getDayOfMonth() <= 26;
    }

    static boolean isHalf(BlockState state) {
        return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
    }

    void renderHalf(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        var template = materials.computeIfAbsent(state.getBlock(),
                block -> ((ChestBlock) block).newBlockEntity(BlockPos.ZERO, state));
        var material = Sheets.chooseMaterial(template, type, holiday);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
        pose.translate(-.5, -.5, -.5);
        (type == ChestType.LEFT ? left : right).render(pose, material.buffer(buffers, RenderType::entityCutout), light, overlay);
        pose.popPose();
    }
}

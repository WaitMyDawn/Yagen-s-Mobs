package yagen.waitmydawn.yagens_mobs.entity.vulture.client;

import yagen.waitmydawn.yagens_mobs.client.RenderPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.time.LocalDate;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState.ChestMaterialType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

final class JoinedChestRenderer {
    private final ModelPart left;
    private final ModelPart right;
    private final boolean holiday;

    JoinedChestRenderer(Function<ModelLayerLocation, ModelPart> bakeLayer) {
        left = bakeLayer.apply(ModelLayers.DOUBLE_CHEST_LEFT);
        right = bakeLayer.apply(ModelLayers.DOUBLE_CHEST_RIGHT);
        LocalDate date = LocalDate.now();
        holiday = date.getMonthValue() == 12 && date.getDayOfMonth() >= 24 && date.getDayOfMonth() <= 26;
    }

    static boolean isHalf(BlockState state) {
        return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
    }

    void submitHalf(BlockState state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        var material = Sheets.chooseMaterial(chestMaterial(state), type);
        var sprite = Minecraft.getInstance().getAtlasManager().get(material);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        RenderPose.rotate(pose, Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
        pose.translate(-.5, -.5, -.5);
        collector.submitModelPart(type == ChestType.LEFT ? left : right, pose, Sheets.chestSheet(), light, overlay, sprite);
        pose.popPose();
    }

    private ChestMaterialType chestMaterial(BlockState state) {
        if (holiday) {
            return ChestMaterialType.CHRISTMAS;
        }
        if (state.getBlock() instanceof TrappedChestBlock) {
            return ChestMaterialType.TRAPPED;
        }
        if (state.is(Blocks.COPPER_CHEST) || state.is(Blocks.WAXED_COPPER_CHEST)) {
            return ChestMaterialType.COPPER_UNAFFECTED;
        }
        if (state.is(Blocks.EXPOSED_COPPER_CHEST) || state.is(Blocks.WAXED_EXPOSED_COPPER_CHEST)) {
            return ChestMaterialType.COPPER_EXPOSED;
        }
        if (state.is(Blocks.WEATHERED_COPPER_CHEST) || state.is(Blocks.WAXED_WEATHERED_COPPER_CHEST)) {
            return ChestMaterialType.COPPER_WEATHERED;
        }
        if (state.is(Blocks.OXIDIZED_COPPER_CHEST) || state.is(Blocks.WAXED_OXIDIZED_COPPER_CHEST)) {
            return ChestMaterialType.COPPER_OXIDIZED;
        }
        return ChestMaterialType.REGULAR;
    }
}

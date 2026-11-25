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
        var material = Sheets.chooseSprite(chestMaterial(state), type);
        var sprite = Minecraft.getInstance().getAtlasManager().get(material);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        RenderPose.rotate(pose, Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
        pose.translate(-.5, -.5, -.5);
        collector.submitModelPart(type == ChestType.LEFT ? left : right, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(Sheets.CHEST_SHEET), light, overlay, sprite);
        pose.popPose();
    }

    private ChestMaterialType chestMaterial(BlockState state) {
        if (holiday) {
            return ChestMaterialType.CHRISTMAS;
        }
        if (state.getBlock() instanceof TrappedChestBlock) {
            return ChestMaterialType.TRAPPED;
        }
        if (state.is(Blocks.COPPER_CHEST.weathering().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED))
                || state.is(Blocks.COPPER_CHEST.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED))) {
            return ChestMaterialType.COPPER_UNAFFECTED;
        }
        if (state.is(Blocks.COPPER_CHEST.weathering().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.EXPOSED))
                || state.is(Blocks.COPPER_CHEST.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.EXPOSED))) {
            return ChestMaterialType.COPPER_EXPOSED;
        }
        if (state.is(Blocks.COPPER_CHEST.weathering().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.WEATHERED))
                || state.is(Blocks.COPPER_CHEST.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.WEATHERED))) {
            return ChestMaterialType.COPPER_WEATHERED;
        }
        if (state.is(Blocks.COPPER_CHEST.weathering().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.OXIDIZED))
                || state.is(Blocks.COPPER_CHEST.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.OXIDIZED))) {
            return ChestMaterialType.COPPER_OXIDIZED;
        }
        return ChestMaterialType.REGULAR;
    }
}

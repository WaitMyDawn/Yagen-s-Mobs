package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.Vulture;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.transport.Cargo;

final class VultureRenderer extends GeoEntityRenderer<Vulture> {
    private static final ResourceLocation ADULT = id("geo/entity/condroc.geo.json");
    private static final ResourceLocation CHICK = id("geo/entity/condroc_juvenile.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/condroc.png");
    private static final ResourceLocation CHICK_TEXTURE = id("textures/entity/condroc_juvenile.png");
    private static final ResourceLocation ANIMATION = id("animations/entity/condroc.animation.json");
    private static final ResourceLocation CHICK_ANIMATION = id("animations/entity/condroc_juvenile.animation.json");
    private final JoinedChestRenderer chests;

    VultureRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(Vulture bird) {
                return bird.isBaby() ? CHICK : ADULT;
            }

            @Override
            public ResourceLocation getTextureResource(Vulture bird) {
                return bird.isBaby() ? CHICK_TEXTURE : TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(Vulture bird) {
                return bird.isBaby() ? CHICK_ANIMATION : ANIMATION;
            }
        });
        chests = new JoinedChestRenderer(context::bakeLayer);
    }

    @Override
    public void render(Vulture bird, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        withScale(bird.getAgeScale());
        shadowRadius = .55F * bird.getAgeScale();
        super.render(bird, yaw, partialTick, stack, buffers, light);
        if (bird.isCarryingMail()) {
            stack.pushPose();
            stack.translate(0, -.17, 0);
            stack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, bird.yBodyRotO, bird.yBodyRot)));
            stack.scale(.42F, .42F, .42F);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.MAIL_BAG.get()),
                    ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, stack, buffers, bird.level(), bird.getId());
            stack.popPose();
        }
        BlockState state = bird.carriedBlock();
        if (state.isAir() || bird.cargoDisplay() == Vulture.CargoDisplay.PARTNER) {
            return;
        }
        if (bird.cargoDisplay() == Vulture.CargoDisplay.DOUBLE_CHEST && JoinedChestRenderer.isHalf(state)) {
            renderDoubleChest(bird, state, partialTick, stack, buffers, light);
        } else {
            stack.pushPose();
            stack.translate(-.5, -.98, -.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, stack, buffers,
                    light, OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
    }

    private void renderDoubleChest(Vulture bird, BlockState state, float tick, PoseStack stack,
                                   MultiBufferSource buffers, int light) {
        BlockState first = Cargo.faceChestPartner(state, Direction.WEST);
        ChestType mate = first.getValue(ChestBlock.TYPE) == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
        // A single pose carries both halves through formation changes and client interpolation.
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(tick, bird.yBodyRotO, bird.yBodyRot)));
        stack.translate(-.5, -14.0 / 16.0, -.5);
        chests.renderHalf(first, stack, buffers, light, OverlayTexture.NO_OVERLAY);
        stack.translate(-1.0, 0, 0);
        chests.renderHalf(first.setValue(ChestBlock.TYPE, mate), stack, buffers, light, OverlayTexture.NO_OVERLAY);
        stack.popPose();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}

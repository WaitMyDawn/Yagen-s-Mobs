package yagen.waitmydawn.yagens_mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.Vulture;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.transport.Cargo;

final class VultureRenderer extends GeckoLivingRenderer<Vulture> {
    private static final DataTicket<Payload> CARGO = DataTicket.create("yagens_mobs:condroc_cargo", Payload.class);
    private static final Identifier ADULT = id("entity/condroc");
    private static final Identifier CHICK = id("entity/condroc_juvenile");
    private static final Identifier TEXTURE = id("textures/entity/condroc.png");
    private static final Identifier CHICK_TEXTURE = id("textures/entity/condroc_juvenile.png");
    private static final Identifier ANIMATION = id("entity/condroc");
    private static final Identifier CHICK_ANIMATION = id("entity/condroc_juvenile");
    private final JoinedChestRenderer chests;

    VultureRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoAppearance<>() {
            @Override
            public Identifier selectModel(Vulture bird) {
                return bird.isBaby() ? CHICK : ADULT;
            }

            @Override
            public Identifier selectTexture(Vulture bird) {
                return bird.isBaby() ? CHICK_TEXTURE : TEXTURE;
            }

            @Override
            public Identifier getAnimationResource(Vulture bird) {
                return bird.isBaby() ? CHICK_ANIMATION : ANIMATION;
            }
        });
        chests = new JoinedChestRenderer(context::bakeLayer);
    }

    @Override
    public void addRenderData(Vulture bird, Void related, State state, float tick) {
        super.addRenderData(bird, related, state, tick);
        ItemStackRenderState mail = null;
        if (bird.isCarryingMail()) {
            mail = new ItemStackRenderState();
            itemModelResolver.updateForLiving(mail, ModItems.MAIL_BAG.get().getDefaultInstance(), ItemDisplayContext.FIXED, bird);
        }
        state.addGeckolibData(CARGO, new Payload(bird.carriedBlock(), bird.cargoDisplay(),
                Mth.rotLerp(tick, bird.yBodyRotO, bird.yBodyRot), mail, bird.getAgeScale()));
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> pass, float width, float height) {
        float scale = pass.getGeckolibData(CARGO).scale();
        super.scaleModelForRender(pass, width * scale, height * scale);
    }

    @Override
    protected float getShadowRadius(State state) {
        return state.isBaby ? .275F : .55F;
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
        super.submit(state, poses, nodes, camera);
        var payload = state.getGeckolibData(CARGO);
        int light = state.getPackedLight();
        if (payload.mail() != null) {
            poses.pushPose();
            poses.translate(0, -.17, 0);
            RenderPose.rotate(poses, Axis.YP.rotationDegrees(-payload.yaw()));
            poses.scale(.42F, .42F, .42F);
            payload.mail().submit(poses, nodes, light, OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
        }
        var block = payload.block();
        if (block.isAir() || payload.display() == Vulture.CargoDisplay.PARTNER) {
            return;
        }
        poses.pushPose();
        if (payload.display() == Vulture.CargoDisplay.DOUBLE_CHEST && JoinedChestRenderer.isHalf(block)) {
            BlockState first = Cargo.faceChestPartner(block, Direction.WEST);
            ChestType mate = first.getValue(ChestBlock.TYPE) == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
            RenderPose.rotate(poses, Axis.YP.rotationDegrees(-payload.yaw()));
            poses.translate(-.5, -14.0 / 16.0, -.5);
            chests.submitHalf(first, poses, nodes, light, OverlayTexture.NO_OVERLAY);
            poses.translate(-1, 0, 0);
            chests.submitHalf(first.setValue(ChestBlock.TYPE, mate), poses, nodes, light, OverlayTexture.NO_OVERLAY);
        } else {
            poses.translate(-.5, -.98, -.5);
            nodes.submitBlock(poses, block, light, OverlayTexture.NO_OVERLAY, 0);
        }
        poses.popPose();
    }

    private record Payload(BlockState block, Vulture.CargoDisplay display, float yaw, ItemStackRenderState mail, float scale) {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }
}

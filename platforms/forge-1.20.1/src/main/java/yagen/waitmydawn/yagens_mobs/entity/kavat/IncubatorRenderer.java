package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class IncubatorRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<Incubator> {
    private final GeoBlockRenderer<Incubator> renderer;

    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
        renderer = new GeoBlockRenderer<>(new DefaultedBlockGeoModel<>(new ResourceLocation(ModIdentity.ID, "kavat_incubator")));
    }

    @Override
    public void render(Incubator chamber, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        chamber.sampleShutters(partialTick);
        poses.pushPose();
        if (chamber.isPacked()) poses.scale(.5f, .5f, .5f);
        poses.translate(.5, 0, .5);
        renderer.render(chamber, partialTick, poses, buffers, light, overlay);
        poses.popPose();
    }

}

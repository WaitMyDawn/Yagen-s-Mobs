package yagen.waitmydawn.yagens_mobs.entity.collector.client;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import yagen.waitmydawn.yagens_mobs.client.GeckoLivingRenderer;
import yagen.waitmydawn.yagens_mobs.client.GeoAppearance;
import yagen.waitmydawn.yagens_mobs.client.RenderPose;
import yagen.waitmydawn.yagens_mobs.entity.collector.Collector;

public final class CollectorRenderer extends GeckoLivingRenderer<Collector> {
    private record Display(List<ItemStack> stacks) {}
    private static final DataTicket<Display> DISPLAY = DataTicket.create("yagens_mobs:collector_display", Display.class);
    private static final List<String> SOCKETS = List.of("sign_1", "sign_2", "sign_3", "cargo_path");

    public CollectorRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withRenderLayer(new BlockAndItemGeoLayer<>(context, this) {
            @Override protected List<RenderData> getRelevantBones(Collector mob, Void context, State state, float tick) {
                var contents = new ArrayList<RenderData>();
                for (int i = 0; i < SOCKETS.size(); i++) {
                    var stack = i == 3 ? mob.cargo() : mob.sign(i);
                    if (stack.isEmpty()) continue;
                    var item = new net.minecraft.client.renderer.item.ItemStackRenderState();
                    itemModelResolver.updateForLiving(item, stack, ItemDisplayContext.GUI, mob);
                    contents.add(RenderData.item(SOCKETS.get(i), ItemDisplayContext.GUI, item));
                }
                return contents;
            }
            @Override public void addRenderData(Collector mob, Void context, State state, float tick) {
                state.addGeckolibData(CONTENTS, getRelevantBones(mob, context, state, tick));
            }
            @Override protected void submitItemStackRender(PoseStack poses, GeoBone bone,
                    net.minecraft.client.renderer.item.ItemStackRenderState stack, ItemDisplayContext display,
                    State state, SubmitNodeCollector nodes, int light) {
                poses.pushPose();
                orient(poses, bone);
                super.submitItemStackRender(poses, bone, stack, display, state, nodes, light);
                poses.popPose();
            }
        });
    }

    private static void orient(PoseStack poses, GeoBone bone) {
        boolean cargo = bone.name().equals("cargo_path");
        RenderPose.rotate(poses, Axis.YP.rotationDegrees(cargo ? 65 : 90));
        float size = cargo ? .24F : .19F;
        poses.scale(size, size, cargo ? size : .014F);
    }

    @Override public void addRenderData(Collector mob, Void context, State state, float tick) {
        super.addRenderData(mob, context, state, tick);
        state.addGeckolibData(DISPLAY, new Display(List.of(mob.sign(0), mob.sign(1), mob.sign(2), mob.cargo())));
    }

    @Override public void adjustModelBonesForRender(RenderPassInfo<State> pass, BoneSnapshots bones) {
        var stacks = pass.getGeckolibData(DISPLAY).stacks();
        for (int i = 0; i < 3; i++) {
            boolean hidden = stacks.get(i).isEmpty();
            bones.get("request_" + (i + 1)).ifPresent(bone -> {
                bone.skipRender(hidden);
                bone.skipChildrenRender(hidden);
            });
        }
    }

    @Override protected float getShadowRadius(State state) { return .52F; }

    private static final class Model extends GeoAppearance<Collector> {
        @Override protected Identifier selectModel(Collector mob) { return Collector.id("entity/collector/" + mob.coat()); }
        @Override protected Identifier selectTexture(Collector mob) { return Collector.id("textures/entity/collector/" + mob.coat() + ".png"); }
        @Override public Identifier getAnimationResource(Collector mob) { return Collector.id("entity/collector"); }
    }
}

package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client;

import net.minecraft.client.resources.model.BakedModel;
import java.util.IdentityHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.AirBladderBlock;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderColor;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class BladderClient {
    private static int tint(BubbleSalamanderColor color) {
        return switch (color) {
            case GREEN -> 0xff90ddac;
            case ALBINO -> 0xfffff1e8;
            case PINK -> 0xffffa9cb;
            case YELLOW -> 0xffffde82;
            case BLACK -> 0xff7d7892;
            case BROWN -> 0xffc79e79;
            case RED -> 0xfff89083;
            case BLUE -> 0xff85b9f7;
            case MIXED -> 0xffffffff;
        };
    }

    @SubscribeEvent
    public static void models(ModelEvent.ModifyBakingResult event) {
        var wrappers = new IdentityHashMap<Block, IdentityHashMap<BakedModel, ConnectedBladderModel>>();
        event.getModels().replaceAll((key, model) -> {
            if (key.variant().equals("inventory")) return model;
            Block block = BuiltInRegistries.BLOCK.get(key.id());
            if (!(block instanceof AirBladderBlock)) return model;
            return wrappers.computeIfAbsent(block, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(model, baked -> new ConnectedBladderModel(baked, block));
        });
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        var handler = (net.minecraft.client.color.block.BlockColor) (state, world, pos, index) -> tint(AirBladderBlock.color(state));
        event.register(handler, ModBlocks.AIR_BLADDER.get());
        ModBlocks.AIR_BLADDERS.values().forEach(block -> event.register(handler, block.get()));
    }

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        var handler = (net.minecraft.client.color.item.ItemColor) (stack, index) -> tint(AirBladderBlock.color(stack));
        event.register(handler, ModItems.AIR_BLADDER.get());
        ModItems.AIR_BLADDERS.values().forEach(item -> event.register(handler, item.get()));
    }

    @SubscribeEvent
    public static void fluid(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "block/air_bladder"); }
            @Override public ResourceLocation getFlowingTexture() { return getStillTexture(); }
            @Override public boolean renderFluid(FluidState fluid, BlockAndTintGetter level, BlockPos pos, VertexConsumer vertices, BlockState state) {
                // The block model supplies the membrane, including its connected edges.
                return true;
            }
        }, BladderFluid.TYPE);
    }
}

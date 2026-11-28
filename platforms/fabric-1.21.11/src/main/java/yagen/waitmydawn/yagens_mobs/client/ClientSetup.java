package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import yagen.waitmydawn.yagens_mobs.entity.salvage.MoltVoidProtection;

import yagen.waitmydawn.yagens_mobs.entity.vulture.client.LongFeatherArrowRenderer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.BlockAndTintGetter;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BladderFluid;
import yagen.waitmydawn.yagens_mobs.guide.FieldGuide;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderClient;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderRenderer;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderTextures;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.client.DesertSkateRenderer;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.LauncherPreview;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.LauncherScreen;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.client.LumenRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.MailBagScreen;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.RockHangerRenderer;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.RockSilkControls;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.RockSilkLineRenderer;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.client.SkateSpikeRenderer;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodControls;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VulturePostRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VultureRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VultureStationScreen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationPackets;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;

public final class ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (net.minecraft.client.Minecraft.getInstance().hasShiftDown() && MoltVoidProtection.coated(stack)) {
                lines.add(Component.translatable("item.yagens_mobs.salvage_ray_molt.coating")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(ModBlocks.AIR_BLADDER.get(), net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        ModBlocks.AIR_BLADDERS.values().forEach(block ->
            net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(block.get(), net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT));
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(ModBlocks.VOID_CLOUD.get(), net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        FluidRenderHandlerRegistry.INSTANCE.register(BladderFluid.MEDIUM, new FluidRenderHandler() {
            @Override public TextureAtlasSprite[] getFluidSprites(BlockAndTintGetter level, BlockPos pos, FluidState fluid) {
                return FluidRenderHandlerRegistry.INSTANCE.get(net.minecraft.world.level.material.Fluids.WATER).getFluidSprites(level, pos, fluid);
            }
            @Override public void renderFluid(BlockPos pos, BlockAndTintGetter level, com.mojang.blaze3d.vertex.VertexConsumer out, BlockState state, FluidState fluid) {
                // The connected membrane block provides all visible geometry.
            }
        });
        if (FieldGuide.isAvailable()) yagen.waitmydawn.yagens_mobs.guide.FieldGuideClient.register();
        yagen.waitmydawn.yagens_mobs.entity.kavat.KavatClient.register();
        EntityRendererRegistry.register(ModEntities.COLLECTOR.get(), yagen.waitmydawn.yagens_mobs.entity.collector.client.CollectorRenderer::new);
        EntityRendererRegistry.register(ModEntities.KAVAT.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.KavatView::new);
        EntityRendererRegistry.register(ModEntities.KAVAT_DECOY.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.KavatView::new);
        BlockEntityRendererRegistry.register(ModBlocks.INCUBATOR_ENTITY.get(),yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorRenderer::new);
        MenuScreens.register(ModBlocks.INCUBATOR_MENU.get(),yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorScreen::new);
        LumenRenderer.register();
        yagen.waitmydawn.yagens_mobs.entity.weasel.client.WeaselClient.register();
        yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageClient.register();
        ClientConfigs.reload();
        MenuScreens.register(MailBagMenu.TYPE.get(), MailBagScreen::new);
        MenuScreens.register(LauncherMenu.TYPE.get(), LauncherScreen::new);
        EntityModelLayerRegistry.registerModelLayer(SkateSpikeRenderer.LAYER, SkateSpikeRenderer::createLayer);
        EntityRendererRegistry.register(ModEntities.DESERT_SKATE.get(), DesertSkateRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKATE_SPIKE.get(), SkateSpikeRenderer::new);
        EntityRendererRegistry.register(ModEntities.VULTURE.get(), VultureRenderer::new);
        EntityRendererRegistry.register(ModEntities.LONG_FEATHER_ARROW.get(), LongFeatherArrowRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        EntityRendererRegistry.register(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        EntityRendererRegistry.register(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_LINE.get(), RockSilkLineRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_GRIP.get(), NoopRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.VULTURE_POST_ENTITY.get(), VulturePostRenderer::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, models) -> {
            if (renderer instanceof AvatarRenderer<?> player) {
                helper.register(new BubbleSalamanderRenderer.WornLayer(player, models));
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(ModIdentity.ID, "kavat_animations");
            }

            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.SharedState state, Executor background,
                    PreparableReloadListener.PreparationBarrier barrier, Executor game) {
                return yagen.waitmydawn.yagens_mobs.entity.kavat.KavatAnimations.INSTANCE.reload(state, background, barrier, game);
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(ModIdentity.ID, "bubble_salamander_textures");
            }

            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.SharedState state, Executor background,
                    PreparableReloadListener.PreparationBarrier barrier, Executor game) {
                return BubbleSalamanderTextures.INSTANCE.reload(state, background, barrier, game);
            }
        });
        BubbleSalamanderClient.registerKeys();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BubbleSalamanderClient.releaseOnKey(client);
            LauncherPreview.tick(client);
            RockSilkControls.tick(client);
            VelocipodControls.sendInput(client);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(RockSilkLineRenderer::renderOffSectionSpans);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LauncherPreview::renderWorld);
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            LauncherPreview.renderHud(graphics);
            VelocipodControls.renderStamina(graphics);
        });
        ClientPlayNetworking.registerGlobalReceiver(StationPackets.Snapshot.TYPE,
                (payload, context) -> VultureStationScreen.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(StationPackets.MailName.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof MailBagMenu menu && menu.containerId == payload.containerId()) {
                menu.updateBoundLabel(payload.name());
            }
        });
    }
}

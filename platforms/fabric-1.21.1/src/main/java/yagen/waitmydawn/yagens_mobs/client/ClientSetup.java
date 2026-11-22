package yagen.waitmydawn.yagens_mobs.client;

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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.transport.MailBagMenu;

public final class ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfigs.reload();
        MenuScreens.register(MailBagMenu.TYPE.get(), MailBagScreen::new);
        MenuScreens.register(LauncherMenu.TYPE.get(), LauncherScreen::new);
        EntityModelLayerRegistry.registerModelLayer(SkateSpikeRenderer.LAYER, SkateSpikeRenderer::createLayer);
        EntityRendererRegistry.register(ModEntities.DESERT_SKATE.get(), DesertSkateRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKATE_SPIKE.get(), SkateSpikeRenderer::new);
        EntityRendererRegistry.register(ModEntities.VULTURE.get(), VultureRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        EntityRendererRegistry.register(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        EntityRendererRegistry.register(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_LINE.get(), RockSilkLineRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_GRIP.get(), NoopRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.VULTURE_POST_ENTITY.get(), VulturePostRenderer::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, models) -> {
            if (renderer instanceof PlayerRenderer player) {
                helper.register(new BubbleSalamanderRenderer.WornLayer(player, models));
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "bubble_salamander_textures");
            }

            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                    ResourceManager manager, ProfilerFiller preparation, ProfilerFiller runtime,
                    Executor background, Executor game) {
                return BubbleSalamanderTextures.INSTANCE.reload(barrier, manager, preparation, runtime, background, game);
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
        WorldRenderEvents.LAST.register(LauncherPreview::renderWorld);
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

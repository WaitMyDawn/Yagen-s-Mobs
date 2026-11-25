package yagen.waitmydawn.yagens_mobs.client;

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
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
        yagen.waitmydawn.yagens_mobs.entity.kavat.KavatClient.register();
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
        ModelLayerRegistry.registerModelLayer(SkateSpikeRenderer.LAYER, SkateSpikeRenderer::createLayer);
        EntityRendererRegistry.register(ModEntities.DESERT_SKATE.get(), DesertSkateRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKATE_SPIKE.get(), SkateSpikeRenderer::new);
        EntityRendererRegistry.register(ModEntities.VULTURE.get(), VultureRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        EntityRendererRegistry.register(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        EntityRendererRegistry.register(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_LINE.get(), RockSilkLineRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCK_SILK_GRIP.get(), NoopRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.VULTURE_POST_ENTITY.get(), VulturePostRenderer::new);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, models) -> {
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
        LevelRenderEvents.COLLECT_SUBMITS.register(RockSilkLineRenderer::renderOffSectionSpans);
        LevelRenderEvents.COLLECT_SUBMITS.register(LauncherPreview::renderWorld);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ModIdentity.ID, "creature_hud"), (graphics, delta) -> {
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

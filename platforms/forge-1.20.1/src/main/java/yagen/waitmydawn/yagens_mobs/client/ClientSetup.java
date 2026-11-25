package yagen.waitmydawn.yagens_mobs.client;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;

import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderClient;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderRenderer;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderTextures;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.client.DesertSkateRenderer;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.LauncherScreen;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.MailBagScreen;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.RockHangerRenderer;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.client.RockSilkLineRenderer;
import yagen.waitmydawn.yagens_mobs.entity.desertskate.client.SkateSpikeRenderer;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodControls;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.VelocipodRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VulturePostRenderer;
import yagen.waitmydawn.yagens_mobs.entity.vulture.client.VultureRenderer;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        BubbleSalamanderClient.registerKeys(event);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(KavatAnimations.INSTANCE);
        event.registerReloadListener(BubbleSalamanderTextures.INSTANCE);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("vasca_rescue", (gui, graphics, partial, width, height) -> KavatRescueView.draw(graphics, partial));
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "velocipod_stamina",
                (gui, graphics, partialTick, width, height) -> VelocipodControls.renderStamina(graphics));
    }

    @SubscribeEvent
    public static void registerMenus(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModBlocks.INCUBATOR_MENU.get(), IncubatorScreen::new);
            MenuScreens.register(MailBagMenu.TYPE.get(), MailBagScreen::new);
            MenuScreens.register(LauncherMenu.TYPE.get(), LauncherScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SkateSpikeRenderer.LAYER, SkateSpikeRenderer::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KAVAT.get(), KavatView::new);
        event.registerEntityRenderer(ModEntities.KAVAT_DECOY.get(), KavatView::new);
        event.registerBlockEntityRenderer(ModBlocks.INCUBATOR_ENTITY.get(), IncubatorRenderer::new);
        event.registerEntityRenderer(ModEntities.DESERT_SKATE.get(), DesertSkateRenderer::new);
        event.registerEntityRenderer(ModEntities.SKATE_SPIKE.get(), SkateSpikeRenderer::new);
        event.registerEntityRenderer(ModEntities.VULTURE.get(), VultureRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        event.registerEntityRenderer(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        event.registerEntityRenderer(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_SILK_LINE.get(), RockSilkLineRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_SILK_GRIP.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.VULTURE_POST_ENTITY.get(), VulturePostRenderer::new);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BubbleSalamanderRenderer.WornLayer(renderer, event.getContext()));
            }
        }
    }
}

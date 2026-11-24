package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.transport.MailBagMenu;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BubbleSalamanderTextures.INSTANCE);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(yagen.waitmydawn.yagens_mobs.kavat.KavatRescue.ID,
                yagen.waitmydawn.yagens_mobs.kavat.KavatRescueView::draw);
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, VelocipodControls.STAMINA, VelocipodControls::renderStamina);
    }

    @SubscribeEvent
    public static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(MailBagMenu.TYPE.get(), MailBagScreen::new);
        event.register(LauncherMenu.TYPE.get(), LauncherScreen::new);
        event.register(ModBlocks.INCUBATOR_MENU.get(), yagen.waitmydawn.yagens_mobs.kavat.IncubatorScreen::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SkateSpikeRenderer.LAYER, SkateSpikeRenderer::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DESERT_SKATE.get(), DesertSkateRenderer::new);
        event.registerEntityRenderer(ModEntities.SKATE_SPIKE.get(), SkateSpikeRenderer::new);
        event.registerEntityRenderer(ModEntities.VULTURE.get(), VultureRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        event.registerEntityRenderer(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        event.registerEntityRenderer(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        event.registerEntityRenderer(ModEntities.KAVAT.get(), yagen.waitmydawn.yagens_mobs.kavat.KavatView::new);
        event.registerEntityRenderer(ModEntities.KAVAT_DECOY.get(), yagen.waitmydawn.yagens_mobs.kavat.KavatView::new);
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

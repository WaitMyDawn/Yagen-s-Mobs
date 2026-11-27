package yagen.waitmydawn.yagens_mobs.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import yagen.waitmydawn.yagens_mobs.entity.salvage.MoltVoidProtection;

import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderRenderer;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.client.BubbleSalamanderTextures;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatAnimations;
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
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class ClientSetup {
    @net.neoforged.bus.api.SubscribeEvent
    public static void clearGuide(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        yagen.waitmydawn.yagens_mobs.guide.client.GuidePlates.clear();
    }

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void showMoltProtection(ItemTooltipEvent event) {
        if (Screen.hasShiftDown() && MoltVoidProtection.coated(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("item.yagens_mobs.salvage_ray_molt.coating")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BubbleSalamanderTextures.INSTANCE);
        event.registerReloadListener(KavatAnimations.INSTANCE);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(yagen.waitmydawn.yagens_mobs.entity.kavat.KavatRescue.ID,
                yagen.waitmydawn.yagens_mobs.entity.kavat.KavatRescueView::draw);
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, VelocipodControls.STAMINA, VelocipodControls::renderStamina);
    }

    @SubscribeEvent
    public static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(MailBagMenu.TYPE.get(), MailBagScreen::new);
        event.register(LauncherMenu.TYPE.get(), LauncherScreen::new);
        event.register(ModBlocks.INCUBATOR_MENU.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorScreen::new);
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
        event.registerEntityRenderer(ModEntities.LONG_FEATHER_ARROW.get(),
                yagen.waitmydawn.yagens_mobs.entity.vulture.client.LongFeatherArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_HANGER.get(), RockHangerRenderer::new);
        event.registerEntityRenderer(ModEntities.BUBBLE_SALAMANDER.get(), BubbleSalamanderRenderer::new);
        event.registerEntityRenderer(ModEntities.VELOCIPOD.get(), VelocipodRenderer::new);
        event.registerEntityRenderer(ModEntities.COLLECTOR.get(), yagen.waitmydawn.yagens_mobs.entity.collector.client.CollectorRenderer::new);
        event.registerEntityRenderer(ModEntities.KAVAT.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.KavatView::new);
        event.registerEntityRenderer(ModEntities.KAVAT_DECOY.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.KavatView::new);
        event.registerEntityRenderer(ModEntities.ROCK_SILK_LINE.get(), RockSilkLineRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCK_SILK_GRIP.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.VULTURE_POST_ENTITY.get(), VulturePostRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.INCUBATOR_ENTITY.get(), yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorRenderer::new);
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

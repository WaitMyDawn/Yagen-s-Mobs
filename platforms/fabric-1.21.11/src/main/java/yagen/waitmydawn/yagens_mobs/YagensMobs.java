package yagen.waitmydawn.yagens_mobs;

import yagen.waitmydawn.yagens_mobs.guide.FieldGuide;
import yagen.waitmydawn.yagens_mobs.entity.command.PetRecall;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.world.item.context.UseOnContext;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.CargoTongs;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.VulturePostEntity;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.SilkLauncher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHangerSilkInteraction;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkInteractions;

import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderWear;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.VelocipodSprintInput;
import yagen.waitmydawn.yagens_mobs.loot.AttackerEnchantmentLevel;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModSounds;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.spawn.ConfiguredMobSpawns;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationData;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationPackets;

public final class YagensMobs implements ModInitializer {
    public static final String MODID = ModIdentity.ID;

    @Override
    public void onInitialize() {
        ServerConfigs.reload(null);
        yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkLine.registerGeometrySerializer();
        StationData.register();
        ModEntities.register();
        ModBlocks.register();
        yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays.register();
        yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels.register();
        ModItems.register();
        if (FieldGuide.isAvailable()) FieldGuide.register();
        LumenButterflies.register();
        yagen.waitmydawn.yagens_mobs.entity.kavat.KavatMemories.register();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                yagen.waitmydawn.yagens_mobs.entity.kavat.KavatRescue.TYPE, yagen.waitmydawn.yagens_mobs.entity.kavat.KavatRescue.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorPage.TYPE, yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorPage.CODEC);
        yagen.waitmydawn.yagens_mobs.entity.CompanionTravel.register();
        ModSounds.register();
        MailBagMenu.register();
        LauncherMenu.register();
        AttackerEnchantmentLevel.register();
        ConfiguredMobSpawns.register();
        StationPackets.register();
        RockSilkPackets.register();
        VelocipodSprintInput.register();
        PetRecall.register();
        BubbleSalamanderWear.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerConfigs.reload(null));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof VulturePostEntity post) post.onLoad();
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> StationData.get(level).tick(level));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var result = RockHangerSilkInteraction.interact(player, hand);
            if (result.consumesAction()) return result;
            result = RockSilkInteractions.recoverFromBlock(player, hand);
            if (result.consumesAction() || player.isSpectator()) return result;
            var stack = player.getItemInHand(hand);
            var context = new UseOnContext(player, hand, hit);
            if (stack.getItem() instanceof CargoTongs tongs) return tongs.onItemUseFirst(stack, context);
            if (stack.getItem() instanceof SilkLauncher launcher) return launcher.onItemUseFirst(stack, context);
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> RockHangerSilkInteraction.interact(player, hand));
    }
}

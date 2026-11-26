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
import net.minecraft.world.InteractionResultHolder;
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

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import yagen.waitmydawn.yagens_mobs.entity.CompanionTravel;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatMemories;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatRescue;
import yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorPage;

public final class YagensMobs implements ModInitializer {
    public static final String MODID = ModIdentity.ID;

    @Override
    public void onInitialize() {
        ServerConfigs.reload(null);
        ModEntities.register();
        ModBlocks.register();
        yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays.register();
        yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels.register();
        ModItems.register();
        if (FieldGuide.isAvailable()) FieldGuide.register();
        LumenButterflies.register();
        KavatMemories.register();
        PayloadTypeRegistry.playS2C().register(
                KavatRescue.TYPE, KavatRescue.CODEC);
        PayloadTypeRegistry.playS2C().register(
                IncubatorPage.TYPE, IncubatorPage.CODEC);
        ServerEntityEvents.ENTITY_LOAD.register(CompanionTravel::joined);
        ServerEntityEvents.ENTITY_UNLOAD.register(CompanionTravel::left);
        ServerTickEvents.END_WORLD_TICK.register(level -> level.players().forEach(CompanionTravel::playerTick));
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
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            var stations = StationData.get(level);
            if (stations.hasActiveFlights()) {
                // Region tickets load chunks while the level's empty timer controls entity ticking.
                level.resetEmptyTime();
            }
            stations.tick(level);
        });

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
        UseItemCallback.EVENT.register((player, level, hand) -> new InteractionResultHolder<>(
                RockHangerSilkInteraction.interact(player, hand), player.getItemInHand(hand)));
    }
}

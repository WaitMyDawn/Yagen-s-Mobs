package yagen.waitmydawn.yagens_mobs;

import yagen.waitmydawn.yagens_mobs.entity.command.PetRecall;
import net.neoforged.bus.api.IEventBus;
import yagen.waitmydawn.yagens_mobs.entity.CompanionTravel;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatCombat;
import yagen.waitmydawn.yagens_mobs.entity.kavat.KavatMemories;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.guide.FieldGuide;
import yagen.waitmydawn.yagens_mobs.guide.FieldNotes;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.BubbleSalamanderWear;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.VelocipodSprintInput;
import yagen.waitmydawn.yagens_mobs.loot.AttackerEnchantmentLevel;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModSounds;
import yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.spawn.ConfiguredMobSpawns;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationData;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationPackets;

@Mod(YagensMobs.MODID)
public final class YagensMobs {
    public static final String MODID = ModIdentity.ID;

    public YagensMobs(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, ModIdentity.CLIENT_CONFIG);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, ModIdentity.SERVER_CONFIG);
        ModEntities.register(modBus);
        LumenButterflies.register(modBus);
        SalvageRays.register(modBus);
        SnowcacheWeasels.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        MailBagMenu.register(modBus);
        LauncherMenu.register(modBus);
        AttackerEnchantmentLevel.register(modBus);
        ConfiguredMobSpawns.register(modBus);
        modBus.addListener(yagen.waitmydawn.yagens_mobs.entity.kavat.IncubatorPage::register);
        modBus.addListener(StationPackets::register);
        modBus.addListener(RockSilkPackets::register);
        modBus.addListener(VelocipodSprintInput::register);
        modBus.addListener(PetRecall::register);
        if (FieldGuide.isAvailable()) {
            modBus.addListener(FieldGuide::register);
            NeoForge.EVENT_BUS.addListener(FieldGuide::onLogin);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) FieldNotes.observe(player);
            });
        }
        modBus.addListener(BubbleSalamanderWear::registerPayloads);
        modBus.addListener(StationData::registerTickets);
        NeoForge.EVENT_BUS.addListener(YagensMobs::tickTransport);
        NeoForge.EVENT_BUS.addListener(CompanionTravel::sectionChanged);
        NeoForge.EVENT_BUS.addListener(CompanionTravel::joined);
        NeoForge.EVENT_BUS.addListener(CompanionTravel::left);
        NeoForge.EVENT_BUS.addListener(CompanionTravel::moved);
        NeoForge.EVENT_BUS.addListener(CompanionTravel::playerTick);
        NeoForge.EVENT_BUS.addListener(KavatCombat::damage);
        NeoForge.EVENT_BUS.addListener(KavatCombat::redirect);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, KavatCombat::rescue);
        NeoForge.EVENT_BUS.addListener(KavatMemories::login);
    }

    private static void tickTransport(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StationData.get(level).tick(level);
        }
    }
}

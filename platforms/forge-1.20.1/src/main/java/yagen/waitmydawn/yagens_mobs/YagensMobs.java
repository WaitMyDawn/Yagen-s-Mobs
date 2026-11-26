package yagen.waitmydawn.yagens_mobs;
import yagen.waitmydawn.yagens_mobs.entity.CompanionTravel;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.loot.AttackerEnchantmentLevel;
import yagen.waitmydawn.yagens_mobs.network.LegacyNetwork;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModSounds;
import yagen.waitmydawn.yagens_mobs.entity.rockhanger.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.spawn.ConfiguredMobSpawns;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.entity.vulture.transport.StationData;

@Mod(YagensMobs.MODID)
public final class YagensMobs {
    public static final String MODID = ModIdentity.ID;

    public YagensMobs() {
        FMLJavaModLoadingContext container = FMLJavaModLoadingContext.get();
        IEventBus modBus = container.getModEventBus();
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, ModIdentity.CLIENT_CONFIG);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, ModIdentity.SERVER_CONFIG);
        ModEntities.register(modBus);
        LumenButterflies.register(modBus);
        yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasels.register(modBus);
        yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRays.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        MailBagMenu.register(modBus);
        LauncherMenu.register(modBus);
        AttackerEnchantmentLevel.register(modBus);
        ConfiguredMobSpawns.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(CompanionTravel::moved);
        MinecraftForge.EVENT_BUS.addListener(CompanionTravel::sectionChanged);
        MinecraftForge.EVENT_BUS.addListener(CompanionTravel::joined);
        MinecraftForge.EVENT_BUS.addListener(CompanionTravel::left);
        MinecraftForge.EVENT_BUS.addListener(CompanionTravel::playerTick);
        MinecraftForge.EVENT_BUS.addListener(KavatCombat::damage);
        MinecraftForge.EVENT_BUS.addListener(KavatCombat::redirect);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, KavatCombat::rescue);
        MinecraftForge.EVENT_BUS.addListener(KavatMemories::login);
        if (yagen.waitmydawn.yagens_mobs.guide.FieldGuide.isAvailable()) {
            yagen.waitmydawn.yagens_mobs.guide.FieldGuide.register();
            MinecraftForge.EVENT_BUS.addListener(yagen.waitmydawn.yagens_mobs.guide.FieldGuide::onLogin);
            MinecraftForge.EVENT_BUS.addListener(yagen.waitmydawn.yagens_mobs.guide.FieldNotes::observe);
        }
        LegacyNetwork.register();
        StationData.registerTickets();
        MinecraftForge.EVENT_BUS.addListener(YagensMobs::tickTransport);
    }

    private static void tickTransport(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            StationData.get(level).tick(level);
        }
    }
}

package yagen.waitmydawn.yagens_mobs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import yagen.waitmydawn.yagens_mobs.butterfly.LumenButterflies;
import yagen.waitmydawn.yagens_mobs.config.ClientConfigs;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import yagen.waitmydawn.yagens_mobs.entity.BubbleSalamanderWear;
import yagen.waitmydawn.yagens_mobs.entity.VelocipodSprintInput;
import yagen.waitmydawn.yagens_mobs.loot.AttackerEnchantmentLevel;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;
import yagen.waitmydawn.yagens_mobs.registries.ModBlocks;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import yagen.waitmydawn.yagens_mobs.registries.ModSounds;
import yagen.waitmydawn.yagens_mobs.silk.LauncherMenu;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkPackets;
import yagen.waitmydawn.yagens_mobs.spawn.ConfiguredMobSpawns;
import yagen.waitmydawn.yagens_mobs.transport.StationData;
import yagen.waitmydawn.yagens_mobs.transport.MailBagMenu;
import yagen.waitmydawn.yagens_mobs.transport.StationPackets;

@Mod(YagensMobs.MODID)
public final class YagensMobs {
    public static final String MODID = ModIdentity.ID;

    public YagensMobs(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, ModIdentity.CLIENT_CONFIG);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, ModIdentity.SERVER_CONFIG);
        ModEntities.register(modBus);
        LumenButterflies.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        MailBagMenu.register(modBus);
        LauncherMenu.register(modBus);
        AttackerEnchantmentLevel.register(modBus);
        ConfiguredMobSpawns.register(modBus);
        modBus.addListener(StationPackets::register);
        modBus.addListener(RockSilkPackets::register);
        modBus.addListener(VelocipodSprintInput::register);
        modBus.addListener(BubbleSalamanderWear::registerPayloads);
        modBus.addListener(StationData::registerTickets);
        NeoForge.EVENT_BUS.addListener(YagensMobs::tickTransport);
    }

    private static void tickTransport(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StationData.get(level).tick(level);
        }
    }
}

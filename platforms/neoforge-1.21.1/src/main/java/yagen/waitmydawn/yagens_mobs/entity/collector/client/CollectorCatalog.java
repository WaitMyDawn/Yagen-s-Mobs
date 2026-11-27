package yagen.waitmydawn.yagens_mobs.entity.collector.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

@EventBusSubscriber(modid = ModIdentity.ID, value = Dist.CLIENT)
public final class CollectorCatalog {
    private CollectorCatalog() {}

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ym_collector_catalog").executes(context -> {
            Minecraft client = Minecraft.getInstance();
            Path path = client.gameDirectory.toPath().resolve("collector-catalog.json").toAbsolutePath();
            try {
                Files.writeString(path, new Gson().toJson(snapshot(client)), StandardCharsets.UTF_8);
                context.getSource().sendSuccess(() -> Component.literal(path.toString()), false);
                return Command.SINGLE_SUCCESS;
            } catch (IOException failure) {
                context.getSource().sendFailure(Component.literal(failure.toString()));
                return 0;
            }
        }));
    }

    public static JsonObject snapshot(Minecraft client) {
        var level = java.util.Objects.requireNonNull(client.level);
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), client.player != null && client.player.hasPermissions(2),
                level.registryAccess());
        var result = new JsonObject();
        result.addProperty("format", 1);
        var items = new JsonObject();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !item.isEnabled(level.enabledFeatures())) continue;
            items.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), item.getDefaultInstance().getHoverName().getString());
        }
        result.add("items", items);
        var tabs = new JsonObject();
        for (var tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            var ids = new LinkedHashSet<String>();
            for (var stack : tab.getDisplayItems()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (items.has(id)) ids.add(id);
            }
            if (ids.isEmpty()) continue;
            var record = new JsonObject();
            record.addProperty("name", tab.getDisplayName().getString());
            record.add("items", new Gson().toJsonTree(ids));
            tabs.add(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString(), record);
        }
        result.add("tabs", tabs);
        return result;
    }
}

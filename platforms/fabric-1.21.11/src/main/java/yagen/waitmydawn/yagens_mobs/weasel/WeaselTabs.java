package yagen.waitmydawn.yagens_mobs.weasel;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class WeaselTabs {
    private static final Identifier OTHER = SnowcacheWeasels.id("uncategorized");
    private static HolderLookup.Provider provider;
    private static Map<Item, Identifier> categories = Map.of();

    private WeaselTabs() {}

    static synchronized void invalidate() {
        provider = null;
    }

    static Identifier of(ItemStack stack) {
        return categories.getOrDefault(stack.getItem(), OTHER);
    }

    static synchronized void prepare(ServerLevel level) {
        if (provider == level.registryAccess()) {
            return;
        }
        provider = level.registryAccess();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, provider);
        Map<Item, Identifier> next = new LinkedHashMap<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.getDisplayItems().size() > 0) {
                Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (key != null) {
                    tab.getDisplayItems().forEach(stack -> next.putIfAbsent(stack.getItem(), key));
                }
            }
        }
        categories = Map.copyOf(next);
    }
}


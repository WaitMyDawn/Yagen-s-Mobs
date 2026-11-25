package yagen.waitmydawn.yagens_mobs.entity.weasel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public record BlueprintPlan(Identifier output, int quantity, Map<String, Choice> choices) {
    public static final int MAX_QUANTITY = 1_000_000;
    public static final int MAX_NODES = 192;
    public static final int MAX_DEPTH = 12;

    public enum Mode { ANY, EXACT, LOOSE }

    public record Choice(Identifier item, @Nullable Identifier recipe, Mode mode) {}

    public BlueprintPlan {
        choices = Map.copyOf(choices);
    }

    public Choice root() { return choices.getOrDefault("", new Choice(output, null, Mode.ANY)); }

    public static String child(String parent, int index) { return parent + "/" + index; }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Output", output.toString());
        tag.putInt("Quantity", quantity);
        var rows = new ListTag();
        choices.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var row = new CompoundTag();
            Choice choice = entry.getValue();
            row.putString("Path", entry.getKey());
            row.putString("Item", choice.item().toString());
            if (choice.recipe() != null) row.putString("Recipe", choice.recipe().toString());
            row.putInt("Mode", choice.mode().ordinal());
            rows.add(row);
        });
        tag.put("Nodes", rows);
        return tag;
    }

    public static @Nullable BlueprintPlan load(CompoundTag tag) {
        var out = parseId(tag.getStringOr("Output", ""));
        int quantity = tag.getIntOr("Quantity", 0);
        var rows = tag.getListOrEmpty("Nodes");
        if (out == null || quantity < 1 || quantity > MAX_QUANTITY || rows.size() > MAX_NODES) return null;
        Map<String, Choice> choices = new LinkedHashMap<>();
        for (Tag entry : rows) {
            if (!(entry instanceof CompoundTag row)) return null;
            String path = row.getStringOr("Path", "");
            var item = parseId(row.getStringOr("Item", ""));
            var recipe = parseId(row.getStringOr("Recipe", ""));
            int mode = row.getIntOr("Mode", 0);
            if (path.length() > MAX_DEPTH * 2 || !validPath(path) || item == null || mode < 0 || mode >= Mode.values().length) return null;
            if (choices.putIfAbsent(path, new Choice(item, recipe, Mode.values()[mode])) != null) return null;
        }
        if (choices.containsKey("") && !Objects.equals(choices.get("").item(), out)) return null;
        return new BlueprintPlan(out, quantity, choices);
    }

    private static boolean validPath(String path) {
        if ((path.length() & 1) != 0) return false;
        for (int i = 0; i < path.length(); i += 2) {
            if (path.charAt(i) != '/' || path.charAt(i + 1) < '0' || path.charAt(i + 1) > '8') return false;
        }
        return true;
    }

    private static @Nullable Identifier parseId(String text) {
        return text.isEmpty() || text.length() > 256 ? null : Identifier.tryParse(text);
    }
}


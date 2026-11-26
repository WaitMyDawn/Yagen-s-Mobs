package yagen.waitmydawn.yagens_mobs.guide.client;

import java.util.List;
import vazkii.patchouli.api.PatchouliAPI;

public final class PatchouliReader {
    public static void open(List<String> known) {
        GuidePlates.setNotes(known);
        PatchouliAPI.get().openBookEntry(GuidePlates.id("field_guide"), GuidePlates.id("catalog"), 0);
    }

    private PatchouliReader() {}
}

package yagen.waitmydawn.yagens_mobs.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class SalvageSpill {
    private static final String MARKER = "yagens_mobs:salvage_spill/";

    private SalvageSpill() {}

    static void mark(ItemEntity item, long until, @Nullable BlockPos box) {
        item.addTag(MARKER + until + (box == null ? "" : "/" + box.asLong()));
    }

    static boolean canCollect(ServerLevel level, ItemEntity item) {
        for (String marker : item.entityTags()) {
            if (!marker.startsWith(MARKER)) {
                continue;
            }
            int separator = marker.indexOf('/', MARKER.length());
            try {
                long until = Long.parseLong(marker.substring(MARKER.length(), separator < 0 ? marker.length() : separator));
                if (level.getGameTime() < until) {
                    return false;
                }
                if (separator >= 0) {
                    BlockPos box = BlockPos.of(Long.parseLong(marker.substring(separator + 1)));
                    if (item.position().distanceToSqr(Vec3.atCenterOf(box.above())) <= 1.4
                            && SalvageNestDirectory.loaded(level, box) != null) {
                        return false;
                    }
                }
            } catch (NumberFormatException ignored) {
                // Command-added tags may contain malformed metadata.
            }
        }
        return true;
    }
}

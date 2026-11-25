package yagen.waitmydawn.yagens_mobs.entity.salvage;

import yagen.waitmydawn.yagens_mobs.compat.SaveTag;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class SalvageNestDirectory extends SavedData {
    private static final SavedDataType<SalvageNestDirectory> STORAGE = new SavedDataType<>(
            "yagens_mobs_salvage_nests", SalvageNestDirectory::new,
            CompoundTag.CODEC.xmap(SalvageNestDirectory::load, data -> data.save(new CompoundTag())), null);
    private final Map<BlockPos, Site> sites = new HashMap<>();
    private final Set<Link> links = new HashSet<>();

    record Site(BlockPos pos, UUID id, String name, @Nullable UUID owner) {
        boolean mayConfigure(Player player) {
            return owner == null || owner.equals(player.getUUID()) || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        }
    }

    record End(BlockPos pos, UUID id) {
        static End of(Site site) { return new End(site.pos(), site.id()); }

        Vec3 marker() { return Vec3.atCenterOf(pos).add(0, .65, 0); }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            SaveTag.putId(tag, "Id", id);
            return tag;
        }

        static End load(CompoundTag tag) {
            return new End(BlockPos.of(tag.getLongOr("Pos", 0L)), SaveTag.id(tag, "Id").orElseThrow());
        }
    }

    record Link(End a, End b) {
        static Link between(End a, End b) {
            return a.pos().asLong() < b.pos().asLong() ? new Link(a, b) : new Link(b, a);
        }

        double distanceSquared(Vec3 point) {
            Vec3 start = a.marker();
            Vec3 span = b.marker().subtract(start);
            double fraction = Math.clamp(point.subtract(start).dot(span) / span.lengthSqr(), 0, 1);
            return point.distanceToSqr(start.add(span.scale(fraction)));
        }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.put("A", a.save());
            tag.put("B", b.save());
            return tag;
        }

        static @Nullable Link read(CompoundTag tag) {
            var a = tag.getCompoundOrEmpty("A");
            var b = tag.getCompoundOrEmpty("B");
            return SaveTag.id(a, "Id").isPresent() && SaveTag.id(b, "Id").isPresent() && a.getLongOr("Pos", 0L) != b.getLongOr("Pos", 0L)
                    ? between(End.load(a), End.load(b)) : null;
        }
    }

    static SalvageNestDirectory of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(STORAGE);
    }

    Site at(BlockPos pos) { return sites.get(pos); }

    @Nullable Site resolve(End end) {
        Site site = at(end.pos());
        return site != null && site.id().equals(end.id()) ? site : null;
    }

    void remember(Site site) {
        Site previous = sites.put(site.pos(), site);
        if (!site.equals(previous)) {
            if (previous != null && !previous.id().equals(site.id())) {
                End replaced = End.of(previous);
                links.removeIf(link -> link.a().equals(replaced) || link.b().equals(replaced));
            }
            setDirty();
        }
    }

    void remove(BlockPos pos, UUID id) {
        Site site = resolve(new End(pos, id));
        if (site != null) {
            End deleted = End.of(site);
            links.removeIf(link -> link.a().equals(deleted) || link.b().equals(deleted));
            sites.remove(pos);
            setDirty();
        }
    }

    boolean connect(End a, End b) {
        if (a.pos().equals(b.pos()) || resolve(a) == null || resolve(b) == null) {
            return false;
        }
        if (links.add(Link.between(a, b))) {
            setDirty();
        }
        return true;
    }

    boolean disconnect(Link link, Player player) {
        Site a = resolve(link.a());
        Site b = resolve(link.b());
        if (a == null || b == null || !a.mayConfigure(player) && !b.mayConfigure(player) || !links.remove(link)) {
            return false;
        }
        setDirty();
        return true;
    }

    boolean connected(End a, End b) {
        return resolve(a) != null && resolve(b) != null && links.contains(Link.between(a, b));
    }

    List<Site> neighbors(End origin) {
        return links.stream().map(link -> link.a().equals(origin) ? resolve(link.b()) : link.b().equals(origin) ? resolve(link.a()) : null)
                .filter(java.util.Objects::nonNull).sorted(Comparator.comparingLong(site -> site.pos().asLong())).toList();
    }

    List<Link> visible(Vec3 point, double range) {
        return links.stream().filter(link -> link.distanceSquared(point) <= range * range)
                .sorted(Comparator.comparingDouble(link -> link.distanceSquared(point))).limit(256).toList();
    }

    List<Site> near(Vec3 point, double range) {
        double squared = range * range;
        return sites.values().stream().filter(site -> point.distanceToSqr(Vec3.atCenterOf(site.pos())) <= squared)
                .sorted(Comparator.comparingDouble((Site site) -> point.distanceToSqr(Vec3.atCenterOf(site.pos())))
                        .thenComparingLong(site -> site.pos().asLong())).toList();
    }

    static @Nullable SalvageNestBoxEntity loaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof SalvageNestBoxEntity box ? box : null;
    }

    private static SalvageNestDirectory load(CompoundTag tag) {
        SalvageNestDirectory data = new SalvageNestDirectory();
        for (Tag row : tag.getListOrEmpty("Sites")) {
            CompoundTag site = (CompoundTag) row;
            if (SaveTag.id(site, "Id").isPresent()) {
                BlockPos pos = BlockPos.of(site.getLongOr("Pos", 0L));
                var owner = SaveTag.id(site, "Owner").isPresent() ? SaveTag.id(site, "Owner").orElseThrow() : null;
                data.sites.put(pos, new Site(pos, SaveTag.id(site, "Id").orElseThrow(), site.getStringOr("Name", ""), owner));
            }
        }
        for (Tag row : tag.getListOrEmpty("Links")) {
            Link link = Link.read((CompoundTag) row);
            if (link != null && data.resolve(link.a()) != null && data.resolve(link.b()) != null) {
                data.links.add(link);
            }
        }
        return data;
    }

    private CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        sites.values().forEach(site -> {
            CompoundTag entry = End.of(site).save();
            entry.putString("Name", site.name());
            if (site.owner() != null) {
                SaveTag.putId(entry, "Owner", site.owner());
            }
            entries.add(entry);
        });
        tag.put("Sites", entries);
        ListTag catalog = new ListTag();
        links.forEach(link -> catalog.add(link.save()));
        tag.put("Links", catalog);
        return tag;
    }
}

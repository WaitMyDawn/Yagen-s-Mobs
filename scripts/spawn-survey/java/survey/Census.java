package survey;

import com.mojang.authlib.GameProfile;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

@Mod("spawn_survey")
public class Census {
    static final List<String> habitats = Arrays.asList(
            System.getProperty("survey.habitats",
                          "snowy_plains,desert,savanna,plains,river,ordinary_cave,dripstone_caves,lush_caves,nether_"
                          + "wastes,basalt_deltas,warped_forest,crimson_forest,soul_sand_valley,the_end,end_highlands")
                    .split(","));
    // Both endpoints are sampled. Capacity snapshots exclude the initial tick.
    static final int duration = Integer.getInteger("survey.ticks", 24000);
    static final int reps = Integer.getInteger("survey.reps", 3);
    static final Map<String, Long> totals = new TreeMap<>();
    static final Set<UUID> seen = new HashSet<>();
    static final PrintWriter population = writer("population"), counts = writer("counts"), sites = writer("sites"),
                             samples = writer("samples"), tests = writer("controls");
    static ServerLevel active;
    static FakePlayer player;
    static BlockPos center;
    static String habitat;
    static int site = -1, ticks;
    static long capTicks;
    public static final boolean diagnostic = Boolean.getBoolean("survey.diagnose");
    static final long[] fullTicks = new long[MobCategory.values().length];
    static final PrintWriter capacity = writer("capacity");
    static final PrintWriter residents = writer("residents");
    public static long chunkTicks;
    public static int chunksThisTick;
    static boolean counting;
    static PrintWriter writer(String name) {
        try {
            return new PrintWriter(new BufferedWriter(new FileWriter(name + ".tsv")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public Census() {
        NeoForge.EVENT_BUS.addListener(Census::tick);
        NeoForge.EVENT_BUS.addListener(Census::join);
        NeoForge.EVENT_BUS.addListener(Census::placement);
    }
    static String biome(LevelReader level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().orElseThrow().location().getPath();
    }
    static String id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }
    public static boolean observing(ServerLevel l) {
        return counting && l == active;
    }

    public static void eligibleChunk(ServerLevel level) {
        if (level != active) return;
        chunksThisTick++;
        if (counting) chunkTicks++;
    }
    public static void count(String stage, ServerLevel l, BlockPos p, EntityType<?> type) {
        String key = stage + "\t" + type.getCategory().getName() + "\t" + id(type) + "\t" + biome(l, p);
        totals.merge(key, 1L, Long::sum);
    }
    public static synchronized void population(WorldGenRegion region, Mob m) {
        var p = m.blockPosition();
        population.printf("%s\t%d\t%d\t%d\t%s\t%s\t%s\t%s%n", region.getLevel().dimension().location().getPath(),
                p.getX(), p.getY(), p.getZ(), biome(region, p), m.getType().getCategory().getName(), id(m.getType()),
                m.getUUID());
    }
    static void join(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() || !(e.getLevel() instanceof ServerLevel l) || !observing(l)
                || !(e.getEntity() instanceof Mob m) || m.getSpawnType() != MobSpawnType.NATURAL
                || !seen.add(m.getUUID()))
            return;
        var p = m.blockPosition();
        count("spawn", l, p, m.getType());
        samples.printf("%d\t%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d%n", site, ticks, biome(l, p),
                m.getType().getCategory().getName(), id(m.getType()), p.getX(), p.getY(), p.getZ(),
                l.getDayTime() % 24000);
    }
    static void placement(MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || !(e.getLevel() instanceof ServerLevel l) || !observing(l))
            return;
        count(e.getPlacementCheckResult() ? "placement_pass" : "placement_fail", l, e.getPos(), e.getEntityType());
        if (!e.getPlacementCheckResult() && e.getEntityType() == ModEntities.ROCK_HANGER.get()) {
            var p = e.getPos();
            String reason = l.getBrightness(LightLayer.BLOCK, p) > 0 ? "block_light"
                    : l.canSeeSky(p)                                 ? "open_sky"
                    : l.getBrightness(LightLayer.SKY, p) > 0         ? "sky_or_geometry"
                                                                     : "anchor_or_clearance";
            count(reason, l, p, e.getEntityType());
        }
    }
    static void controls(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(320, 205, 320);
        for (var b : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 4, 4))) {
            boolean shell = b.getY() == pos.getY() - 1 || b.getY() == pos.getY() + 4
                    || Math.abs(b.getX() - pos.getX()) == 4 || Math.abs(b.getZ() - pos.getZ()) == 4;
            level.setBlock(b, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }
    static void verify(MinecraftServer server) {
        var l = server.overworld();
        var p = new BlockPos(320, 205, 320);
        var kat = ModEntities.KAVAT.get();
        var stone = List.of(Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.SNOW_BLOCK, Blocks.NETHERRACK,
                Blocks.END_STONE);
        for (var difficulty : List.of(Difficulty.NORMAL, Difficulty.PEACEFUL)) {
            server.setDifficulty(difficulty, true);
            for (boolean bright : List.of(false, true))
                for (var floor : stone) {
                    BlockPos x = bright ? p.above(12) : p;
                    l.setBlock(x.below(), floor.defaultBlockState(), 2);
                    for (var type : List.of(kat, EntityType.ENDERMAN)) {
                        var mob = type.create(l);
                        mob.moveTo(x.getX() + .5, x.getY(), x.getZ() + .5, 0, 0);
                        int ok = 0;
                        for (int i = 0; i < 100; i++)
                            if (SpawnPlacements.isSpawnPositionOk(type, l, x)
                                    && SpawnPlacements.checkSpawnRules(type, l, MobSpawnType.NATURAL, x, l.random)
                                    && EventHooks.checkSpawnPosition(mob, l, MobSpawnType.NATURAL))
                                ok++;
                        tests.printf("%s\t%s\t%d\t%s\t%s\t%d%n", difficulty, bright ? "day" : "dark",
                                l.getMaxLocalRawBrightness(x), BuiltInRegistries.BLOCK.getKey(floor), id(type), ok);
                        if (type == kat && ok != 100)
                            throw new IllegalStateException("Kavat control rejected " + x + " " + floor);
                    }
                }
        }
        server.setDifficulty(Difficulty.NORMAL, true);
        tests.flush();
    }
    static boolean cave(String h) {
        return Set.of("ordinary_cave", "dripstone_caves", "lush_caves").contains(h);
    }
    static boolean nether(String h) {
        return Set.of("warped_forest", "crimson_forest", "basalt_deltas", "nether_wastes", "soul_sand_valley")
                .contains(h);
    }
    static BlockPos locate(ServerLevel l, String h, int r, int attempt) {
        int offset = (habitats.indexOf(h) + 1) * 6144;
        BlockPos origin = new BlockPos(offset + attempt * 2048, cave(h) ? -22 : 64, 2048 + r * 8192);
        if (h.equals("the_end")) {
            int x = (r - 1) * 34, z = r * 8 - 20;
            l.getChunk(x >> 4, z >> 4);
            return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        boolean generic = h.equals("ordinary_cave");
        var match = l.findClosestBiome3d(b
                -> generic ? b.is(net.minecraft.world.level.biome.Biomes.PLAINS)
                           : b.unwrapKey().orElseThrow().location().getPath().equals(h),
                origin, 10000, 48, 24);
        if (match == null) return null;
        BlockPos guess = match.getFirst();
        for (int radius = 0; radius <= 160; radius += 8)
            for (int dx = -radius; dx <= radius; dx += 8)
                for (int dz = -radius; dz <= radius; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = guess.getX() + dx, z = guess.getZ() + dz;
                    l.getChunk(x >> 4, z >> 4);
                    if (!cave(h) && !nether(h)) {
                        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        var p = new BlockPos(x, y, z);
                        if (y > l.getMinBuildHeight() + 2 && biome(l, p).equals(h)) return p;
                    } else {
                        for (int y = nether(h) ? 96 : 42; y >= (nether(h) ? 24 : -55); y--) {
                            var p = new BlockPos(x, y, z);
                            if (!(generic ? !Set.of("deep_dark", "lush_caves", "dripstone_caves").contains(biome(l, p))
                                          : biome(l, p).equals(h)))
                                continue;
                            if (l.canSeeSky(p)
                                    || !l.getBlockState(p.below()).isFaceSturdy(
                                            l, p.below(), net.minecraft.core.Direction.UP)
                                    || !l.isEmptyBlock(p))
                                continue;
                            int gap = 1;
                            while (gap < 33 && l.isEmptyBlock(p.above(gap))) gap++;
                            if (gap >= (cave(h) && r == 1 ? 16 : 3) && !(cave(h) && r == 0 && gap >= 16)) return p;
                        }
                    }
                }
        return null;
    }
    static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int eligible = chunksThisTick;
        chunksThisTick = 0;
        if (server.getTickCount() == 1) {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, server);
            server.overworld().setDayTime(6000);
            server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE / 4);
            controls(server);
            if (Boolean.getBoolean("survey.rulesOnly")) {
                ServerLevel end = server.getLevel(Level.END);
                end.setChunkForced(0, 0, true);
                end.getChunk(0, 0);
            }
        }
        if (server.getTickCount() < 100) return;
        if (site == -1 && server.getTickCount() == 100) {
            if (Boolean.getBoolean("survey.rulesOnly")) {
                RuleChecks.verify(server);
                server.halt(false);
                return;
            }
            verify(server);
            site = Integer.getInteger("survey.from", 0) - 1;
        }
        if (active != null && ticks < duration) {
            // Tick sprint can otherwise advance an entire sample while the region is loading asynchronously.
            if (diagnostic && ticks == -1 && eligible < 49) return;
            ticks++;
            if (ticks % 6000 == 0) {
                System.out.println("SURVEY_PROGRESS " + site + " " + habitat + " " + ticks + " " + seen.size());
                samples.flush();
            }
            if (ticks == 0) {
                counting = true;
                server.overworld().setDayTime(0);
            }
            if (counting && active.getChunkSource().getLastSpawnState() != null)
                capTicks += active.getChunkSource().getLastSpawnState().getSpawnableChunkCount();
            if (diagnostic && ticks > 0) {
                var state = active.getChunkSource().getLastSpawnState();
                if (state != null)
                    for (var category : MobCategory.values()) {
                        if (category == MobCategory.MISC) continue;
                        int limit = category.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / 289;
                        if (state.getMobCategoryCounts().getInt(category) >= limit) fullTicks[category.ordinal()]++;
                    }
            }
            return;
        }
        counting = false;
        if (active != null) {
            for (var e : totals.entrySet()) counts.printf("%d\t%s\t%d%n", site, e.getKey(), e.getValue());
            // Snapshot living residents in the simulation square separately from cumulative arrivals.
            var chunk = new ChunkPos(center);
            var region = new net.minecraft.world.phys.AABB((chunk.x - 3) * 16, active.getMinBuildHeight(),
                    (chunk.z - 3) * 16, (chunk.x + 4) * 16, active.getMaxBuildHeight(), (chunk.z + 4) * 16);
            var residentCounts = new TreeMap<String, Integer>();
            for (Mob resident : active.getEntitiesOfClass(Mob.class, region, Mob::isAlive)) {
                residentCounts.merge(id(resident.getType()), 1, Integer::sum);
            }
            residentCounts.forEach((id, count) -> residents.printf("%d\t%s\t%d%n", site, id, count));
            residents.flush();
            sites.printf("done\t%d\t%d\t%d%n", site, ticks, capTicks);
            if (diagnostic) {
                for (var category : MobCategory.values())
                    if (category != MobCategory.MISC)
                        capacity.printf("%d\t%s\t%s\t%d\t%d\t%d%n", site, habitat, category.getName(), ticks,
                                fullTicks[category.ordinal()], chunkTicks);
                capacity.flush();
                Arrays.fill(fullTicks, 0);
                chunkTicks = 0;
            }
            counts.flush();
            sites.flush();
            samples.flush();
            population.flush();
            active.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        if (++site >= habitats.size() * reps) {
            System.out.println("SURVEY_COMPLETE " + server.overworld().getSeed());
            server.halt(false);
            return;
        }
        habitat = habitats.get(site % habitats.size());
        int rep = site / habitats.size();
        active = server.getLevel(switch (habitat) {
            case "the_end", "end_highlands" -> Level.END;
            default -> nether(habitat) ? Level.NETHER : Level.OVERWORLD;
        });
        center = null;
        for (int attempt = 0; center == null && attempt < 5; attempt++) center = locate(active, habitat, rep, attempt);
        if (center == null) throw new IllegalStateException("No suitable terrain " + habitat + " rep " + rep);
        var chunk = new ChunkPos(center);
        sites.printf("start\t%d\t%s\t%d\t%s\t%d\t%d\t%d\t%d\t%d%n", site, habitat, rep,
                active.dimension().location().getPath(), center.getX(), center.getY(), center.getZ(), chunk.x, chunk.z);
        sites.flush();
        for (int dx = -8; dx < 8; dx++)
            for (int dz = -8; dz < 8; dz++) active.getChunk(chunk.x + dx, chunk.z + dz);
        population.flush();
        player = new FakePlayer(active, new GameProfile(UUID.randomUUID(), "Survey"));
        player.moveTo(center.getX() + .5, center.getY(), center.getZ() + .5, 0, 0);
        active.addNewPlayer(player);
        active.getChunkSource().move(player);
        ticks = -200;
        capTicks = 0;
        totals.clear();
        seen.clear();
    }
}

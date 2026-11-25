package survey;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import yagen.waitmydawn.yagens_mobs.registries.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.neoforged.neoforge.event.EventHooks;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

final class RuleChecks {
    static void verify(MinecraftServer server) {
        var type = ModEntities.KAVAT.get();
        boolean enabled = Boolean.parseBoolean(System.getProperty("survey.enabled", "true"));
        int weight = Integer.getInteger("survey.weight", 10);
        int min = Integer.getInteger("survey.min", 1);
        int max = Integer.getInteger("survey.max", 4);
        for (var key : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel level = server.getLevel(key);
            BlockPos pos = new BlockPos(256, 100, 256);
            var biome = level.getBiome(pos).value().getMobSettings();
            var entries = biome.getMobs(MobCategory.CREATURE).unwrap().stream().filter(e -> e.type == type).toList();
            require(type.getCategory() == MobCategory.CREATURE && entries.size() == (enabled ? 1 : 0), "creature pool");
            require(biome.getMobs(MobCategory.MONSTER).unwrap().stream().noneMatch(e -> e.type == type),
                    "monster pool");
            require(!enabled
                            || (entries.getFirst().getWeight().asInt() == weight * (key == Level.NETHER ? 2 : 1)
                                    && entries.getFirst().minCount == min
                                    && entries.getFirst().maxCount == max),
                    "weight and group");
            var mob = type.create(level);
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            require(mob.getMaxSpawnClusterSize() == max, "cluster limit");
            for (Difficulty difficulty : List.of(Difficulty.NORMAL, Difficulty.PEACEFUL)) {
                server.setDifficulty(difficulty, true);
                for (String shape : List.of("floor", "missing_floor", "water", "solid")) {
                    level.setBlockAndUpdate(pos.below(),
                            (shape.equals("missing_floor") ? Blocks.AIR : Blocks.STONE).defaultBlockState());
                    level.setBlockAndUpdate(pos,
                            (shape.equals("water")                  ? Blocks.WATER
                                            : shape.equals("solid") ? Blocks.STONE
                                                                    : Blocks.AIR)
                                    .defaultBlockState());
                    level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                    for (MobSpawnType reason : List.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION)) {
                        boolean pass = SpawnPlacements.isSpawnPositionOk(type, level, pos)
                                && SpawnPlacements.checkSpawnRules(type, level, reason, pos, level.random)
                                && EventHooks.checkSpawnPosition(mob, level, reason);
                        boolean expected = enabled && shape.equals("floor");
                        require(pass == expected, key.location() + " " + reason + " " + difficulty + " " + shape);
                        Census.tests.printf("%s\t%s\t%s\t%s\t%s%n", key.location(), reason, difficulty, shape, pass);
                    }
                }
            }
        }
        if (enabled) verifyEcology(server);
        Census.tests.flush();
        if (Boolean.getBoolean("survey.fortress")) fortress(server.getLevel(Level.NETHER));
        System.out.println("SURVEY_RULES_PASS enabled=" + enabled + " weight=" + weight + " group=" + min + "-" + max);
    }

    private static void verifyEcology(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (String id : List.of("kavat", "desert_skate", "rock_hanger", "condroc", "velocipod",
                "bubble_salamander", "snowcache_weasel", "lumen_butterfly", "salvage_ray")) {
            EntityType<?> type = EntityType.byString("yagens_mobs:" + id).orElseThrow();
            Mob mob = (Mob) type.create(world);
            require(!mob.removeWhenFarAway(1024 * 1024), id + " distance retention");
        }
        ServerLevel end = server.getLevel(Level.END);
        BlockPos home = new BlockPos(0, 160, 0);
        var type = ModEntities.KAVAT.get();
        var resident = type.create(end);
        resident.moveTo(home.getCenter());
        require(end.addFreshEntity(resident), "main island resident");
        require(end.getEntity(resident.getUUID()) == resident, "fixture entity must be visible in the loaded island chunk");
        var newcomer = type.create(end);
        newcomer.moveTo(home.offset(4, 0, 0).getCenter());
        require(!newcomer.checkSpawnRules(end, MobSpawnType.NATURAL), "main island spacing");
        newcomer.moveTo(home.offset(68, 0, 0).getCenter());
        require(newcomer.checkSpawnRules(end, MobSpawnType.NATURAL), "room for a distant resident");
        resident.discard();
        var messages = new ArrayList<TranslatableContents>();
        var sampler = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "Sampler")) {
            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                if (actionBar && message.getContents() instanceof TranslatableContents args) messages.add(args);
            }
        };
        var cat = type.create(world);
        sampler.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KAVAT_EXTRACTOR.get(), 2));
        cat.mobInteract(sampler, InteractionHand.MAIN_HAND);
        int copies = sampler.getInventory().countItem(ModItems.KAVAT_CODE.get());
        require(copies >= 1 && copies <= 3 && messages.size() == 1 && messages.getFirst().getKey().equals("kavat.sample_received")
                && messages.getFirst().getArgs()[1].equals(copies), "sample message count matches items");
        cat.mobInteract(sampler, InteractionHand.MAIN_HAND);
        require(sampler.getInventory().countItem(ModItems.KAVAT_CODE.get()) == copies
                && sampler.getMainHandItem().getCount() == 1 && messages.getLast().getKey().equals("kavat.extracted"),
                "repeat extraction rejected");
    }

    private static void fortress(ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var fortress = registry.getHolderOrThrow(BuiltinStructures.FORTRESS);
        var generator = level.getChunkSource().getGenerator();
        // This chunk exercised a cross-chunk structure reference during Nether population.
        level.getChunk(4219, 97);
        var hit = generator.findNearestMapStructure(
                level, HolderSet.direct(fortress), new BlockPos(67504, 64, 1552), 64, false);
        require(hit != null, "fortress locate");
        var start = level.getChunkAt(hit.getFirst()).getStartForStructure(fortress.value());
        require(start != null && start.isValid(), "fortress start");
        var pos = start.getPieces().getFirst().getBoundingBox().getCenter();
        level.setBlockAndUpdate(pos.below(), Blocks.NETHER_BRICKS.defaultBlockState());
        for (int i = 0; i < 4; i++) level.setBlockAndUpdate(pos.above(i), Blocks.AIR.defaultBlockState());
        var type = ModEntities.KAVAT.get();
        var initial = type.create(level);
        initial.moveTo(pos.getCenter());
        var data = initial.saveWithoutId(new CompoundTag());
        data.putBoolean("PendingFortressCoat", true);
        var loaded = type.create(level);
        loaded.load(data);
        require(loaded.saveWithoutId(new CompoundTag()).getBoolean("PendingFortressCoat"), "pending survives save");
        loaded.tick();
        require(loaded.genes().coat() == 8
                        && !loaded.saveWithoutId(new CompoundTag()).getBoolean("PendingFortressCoat"),
                "resolve fortress coat once");
        System.out.println(
                "SURVEY_FORTRESS_PASS seed=" + level.getSeed() + " chunk=4219,97 fortress=" + pos.toShortString());
    }
    private static void require(boolean pass, String description) {
        if (!pass) throw new AssertionError(description);
    }
}

package yagen.waitmydawn.yagens_mobs.guide;

import yagen.waitmydawn.yagens_mobs.entity.collector.Collector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import yagen.waitmydawn.yagens_mobs.entity.bubblesalamander.*;
import yagen.waitmydawn.yagens_mobs.entity.butterfly.*;
import yagen.waitmydawn.yagens_mobs.entity.kavat.*;
import yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRay;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;
import yagen.waitmydawn.yagens_mobs.entity.weasel.SnowcacheWeasel;

public final class FieldNotes {
    public static final List<String> SPECIES = readSpecies();

    private static List<String> readSpecies() {
        var source = java.util.Objects.requireNonNull(FieldNotes.class.getResourceAsStream("species.txt"));
        try (var reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String NOTES = "yagens_mobs_field_notes";
    private FieldNotes() {}

    public static List<String> variants(String species) {
        if (species.equals("collector")) return java.util.Arrays.stream(Collector.Coat.values()).map(coat -> coat.key).toList();
        var result = new ArrayList<String>();
        if (species.equals("kavat")) {
            for (var group : List.of(KavatGenes.BREEDS, KavatGenes.EARS, KavatGenes.TAILS, KavatGenes.COATS)) result.addAll(group);
        } else if (species.equals("rock_hanger")) result.add("shale");
        else for (String age : List.of("adult", "young")) {
            switch (species) {
                case "bubble_salamander" -> {
                    for (var color : BubbleSalamanderColor.values()) for (String sex : List.of("male", "female"))
                        result.add(age + "/" + color.id() + "/" + sex);
                }
                case "velocipod" -> { for (var color : Velocipod.Color.values()) result.add(age + "/" + color.id()); }
                case "lumen_butterfly" -> {
                    for (var color : LumenColor.values()) result.add(age + "/" + color.id());
                    result.add(age + "/rainbow");
                }
                case "salvage_ray" -> {
                    for (String color : SalvageRay.COLORS) for (int tails = 1; tails <= 3; tails++)
                        result.add(age + "/" + color + "/" + tails);
                }
                case "snowcache_weasel" -> {
                    for (String color : List.of("blue", "light_blue", "light_pink", "white")) result.add(age + "/" + color);
                }
                default -> result.add(age);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> observed(Mob mob) {
        if (mob instanceof Collector collector) return List.of(collector.coat());
        String age = mob.isBaby() ? "young" : "adult";
        if (mob instanceof Kavat cat) {
            var g = cat.genes();
            return cat.decoy() ? List.of() : List.of(KavatGenes.BREEDS.get(g.breed()), KavatGenes.EARS.get(g.ear()),
                    KavatGenes.TAILS.get(g.tail()), KavatGenes.COATS.get(g.coat()));
        }
        if (mob instanceof BubbleSalamander s) return List.of(age + "/" + s.color().id() + "/" + (s.isFemale() ? "female" : "male"));
        if (mob instanceof LumenButterfly b) return List.of(age + "/" + (b.isRainbow() ? "rainbow" : b.color().id()));
        if (mob instanceof Velocipod v) return List.of(age + "/" + v.color().id());
        if (mob instanceof SalvageRay r) return List.of(age + "/" + r.color() + "/" + r.tails());
        if (mob instanceof SnowcacheWeasel w) return List.of(age + "/" + w.color());
        return List.of(mob instanceof yagen.waitmydawn.yagens_mobs.entity.rockhanger.RockHanger ? "shale" : age);
    }

    static CompoundTag notes(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(NOTES);
    }

    public static void observe(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isScoping() || player.tickCount % 4 != 0) return;
        if (!FieldGuide.canRead(player)) return;
        var level = player.serverLevel();
        var eye = player.getEyePosition();
        var end = eye.add(player.getLookAngle().scale(64));
        var stop = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        double nearest = eye.distanceToSqr(stop);
        net.minecraft.world.entity.Entity target = null;
        for (var entity : level.getEntities(player, new AABB(eye, stop).inflate(.1), e -> e.isPickable() && e.isAlive() && !e.isSpectator())) {
            var hit = entity.getBoundingBox().clip(eye, stop);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                target = entity;
                nearest = eye.distanceToSqr(hit.get());
            }
        }
        if (!(target instanceof Mob mob) || mob.isInvisibleTo(player)) return;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!id.getNamespace().equals("yagens_mobs") || !SPECIES.contains(id.getPath())) return;
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        var notes = persisted.getCompound(NOTES);
        boolean changed = false;
        for (var v : observed(mob)) {
            String key = id.getPath() + "/" + v;
            if (!notes.getBoolean(key)) {
                notes.putBoolean(key, true);
                changed = true;
            }
        }
        if (changed) {
            persisted.put(NOTES, notes);
            data.put(Player.PERSISTED_NBT_TAG, persisted);
            player.displayClientMessage(Component.translatable("guide.yagens_mobs.recorded", mob.getType().getDescription()), true);
        }
    }
}

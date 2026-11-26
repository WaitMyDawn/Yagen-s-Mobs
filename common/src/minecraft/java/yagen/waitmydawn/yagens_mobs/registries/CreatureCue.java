package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public enum CreatureCue {
    SAND_SWIM("desert_skate.swim"),
    BUBBLE_ATTACH("bubble_salamander.attach"),
    BUBBLE_RELEASE("bubble_salamander.release"),
    KAVAT_ANSWER("kavat.answer"),
    BUTTERFLY_IDLE("lumen_butterfly.idle"),
    CONDROC_ADULT("condroc.adult"),
    CONDROC_CHICK("condroc.chick"),
    CONDROC_DEATH("condroc.death"),
    CONDROC_DIVE("condroc.dive"),
    CONDROC_EAT("condroc.eat"),
    CONDROC_FLAP("condroc.flap"),
    CONDROC_HATCH("condroc.hatch"),
    CONDROC_HURT("condroc.hurt"),
    CONDROC_PERCH("condroc.perch"),
    HANGER_CLIMB("rock_hanger.climb"),
    HANGER_DEATH("rock_hanger.death"),
    HANGER_FEED("rock_hanger.feed"),
    HANGER_HANG("rock_hanger.hang"),
    HANGER_HURT("rock_hanger.hurt"),
    HANGER_WARN("rock_hanger.warn"),
    KAVAT_DEATH("kavat.death"),
    KAVAT_IDLE("kavat.idle"),
    KAVAT_POUNCE("kavat.pounce"),
    KAVAT_PURR("kavat.purr"),
    KAVAT_RESCUE("kavat.rescue"),
    RAY_CATCH("salvage_ray.catch"),
    RAY_DEATH("salvage_ray.death"),
    RAY_DEPOSIT("salvage_ray.deposit"),
    RAY_HURT("salvage_ray.hurt"),
    RAY_IDLE("salvage_ray.idle"),
    SKATE_DEATH("desert_skate.death"),
    SKATE_DIVE("desert_skate.dive"),
    SKATE_EAT("desert_skate.eat"),
    SKATE_EMERGE("desert_skate.emerge"),
    SKATE_HURT("desert_skate.hurt"),
    SKATE_IDLE("desert_skate.idle"),
    SKATE_SPINE("desert_skate.spine"),
    VELOCIPOD_WING("velocipod.wing"),
    WEASEL_DEATH("snowcache_weasel.death"),
    WEASEL_DELIVER("snowcache_weasel.deliver"),
    WEASEL_HURT("snowcache_weasel.hurt"),
    WEASEL_IDLE("snowcache_weasel.idle");

    final String path;
    Supplier<SoundEvent> sound;

    CreatureCue(String path) {
        this.path = path;
    }

    public @Nullable SoundEvent orElse(@Nullable SoundEvent fallback) {
        return sound != null ? sound.get() : fallback;
    }

    public void playReplacing(Entity entity, float volume, SoundEvent fallback, float fallbackPitch) {
        if (!play(entity, volume)) entity.playSound(fallback, volume, fallbackPitch);
    }

    public boolean play(Entity entity, float volume) {
        if (sound == null) return false;
        entity.playSound(sound.get(), volume, 1.0F);
        return true;
    }
}

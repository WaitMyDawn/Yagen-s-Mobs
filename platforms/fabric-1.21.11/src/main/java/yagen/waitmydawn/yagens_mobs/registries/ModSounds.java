package yagen.waitmydawn.yagens_mobs.registries;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ModSounds {
    public static final Supplier<SoundEvent> VULTURE_CHICK = sound("condroc.chick");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> sound(String name) {
        var id = Identifier.fromNamespaceAndPath(ModIdentity.ID, name);
        var sound = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
        return () -> sound;
    }

    public static void register() {
    }
}

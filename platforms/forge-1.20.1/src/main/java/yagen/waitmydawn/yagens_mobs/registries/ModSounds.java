package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ModIdentity.ID);

    public static final RegistryObject<SoundEvent> VULTURE_CHICK = sound("condroc.chick");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        ResourceLocation id = new ResourceLocation(ModIdentity.ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus bus) {
        DeferredRegister<SoundEvent> calls = DeferredRegister.create(Registries.SOUND_EVENT, ModIdentity.ID + "_audio");
        for (var cue : CreatureCue.values()) {
            var id = new ResourceLocation(ModIdentity.ID + "_audio", cue.path);
            cue.sound = calls.register(cue.path, () -> SoundEvent.createVariableRangeEvent(id));
        }
        calls.register(bus);
        SOUNDS.register(bus);
    }
}

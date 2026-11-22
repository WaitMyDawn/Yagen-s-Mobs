package yagen.waitmydawn.yagens_mobs.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ModIdentity.ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> VULTURE_CHICK = sound("condroc.chick");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}

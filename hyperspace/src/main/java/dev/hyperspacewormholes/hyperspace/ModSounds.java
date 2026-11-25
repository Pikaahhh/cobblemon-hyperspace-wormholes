package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hyperspace.MOD_ID);
    public static final List<SoundEvent> HYPERSPACE_SONGS = List.of(register("music.hyperspace"), register("music.dream_az"),
            register("music.hyperspace_lumiose_field"), register("music.hyperspace_lumiose_field_2"),
            register("music.hyperspace_special_field"));
    public static final SoundEvent WALL_BUMP = register("hyperspace.wall_bump");

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = Hyperspace.id(name);
        SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
        SOUNDS.register(name, () -> sound);
        return sound;
    }
}

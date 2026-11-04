package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

public class ModSounds {
    public static final List<SoundEvent> HYPERSPACE_SONGS = List.of(register("music.hyperspace"), register("music.dream_az"),
            register("music.hyperspace_lumiose_field"), register("music.hyperspace_lumiose_field_2"),
            register("music.hyperspace_special_field"));
    public static final SoundEvent WALL_BUMP = register("hyperspace.wall_bump");

    public static void register() {
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = Hyperspace.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}

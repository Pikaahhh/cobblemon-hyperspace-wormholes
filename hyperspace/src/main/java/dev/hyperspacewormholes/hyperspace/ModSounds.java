package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Registers the Hyperspace sounds.
 */
public class ModSounds {
    public static final List<SoundEvent> HYPERSPACE_SONGS = List.of(register("music.hyperspace"), register("music.dream_az"));
    public static final SoundEvent WALL_BUMP = register("hyperspace.wall_bump");

    public static void register() {
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}

package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.mixin.SoundEngineAccessor;
import dev.hyperspacewormholes.wormholes.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Quietens the world under the white of a ride: every sound but the ride's theme is turned down by as much as the
 * picture is white (SoundEngineMixin), and the worlds' own themes too once the white after the ride has cleared, as
 * they fade into each other by themselves until then. It's never turned down to nothing, as the game doesn't play a
 * sound that starts with no volume.
 */
public final class RideHush {
    private static final float LEAST = 0.0005f;
    private static float others = 1f;
    private static boolean ride;

    private RideHush() {}

    static void set(Minecraft mc, float quiet) {
        if (UltraWormholeMusic.isPlaying()) ride = true;
        else if (quiet <= 0) ride = false;
        float now = Math.max(LEAST, 1 - quiet);
        if (now == others) return;
        others = now;
        var engine = (SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).hyperspace_wormholes$getSoundEngine();
        if (!engine.hyperspace_wormholes$isLoaded()) return;
        engine.hyperspace_wormholes$getInstanceToChannel().forEach((sound, handle) -> {
            float volume = engine.hyperspace_wormholes$calculateVolume(sound);
            handle.execute(channel -> channel.setVolume(volume));
        });
    }

    /** How much of this sound is heard. */
    public static float of(SoundInstance sound) {
        return sound instanceof UltraWormholeMusic || ride && sound instanceof UltraWorldMusic ? 1f : others;
    }
}

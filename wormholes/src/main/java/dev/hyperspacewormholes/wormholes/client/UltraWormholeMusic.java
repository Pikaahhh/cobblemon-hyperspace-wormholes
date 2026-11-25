package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Ultra Wormhole theme. Starts as you touch a wormhole, loops through the ride on the music channel, keeps vanilla
 * music quiet, and fades into the next world's theme as the ride comes out there.
 */
public final class UltraWormholeMusic extends AbstractTickableSoundInstance {
    private static final float FADE_TICKS = 40f, DUCK_TICKS = 50f, BACK_TICKS = 20f;
    private static final float DUCKED = 0.6f;
    private static final int VANILLA_OFF_TICKS = 40;

    private static UltraWormholeMusic current;

    private boolean fading;
    private boolean vanillaOff;
    private float level = 1f;
    private int ticks;
    private boolean rideSeen;

    private UltraWormholeMusic() {
        super(Wormholes.ULTRA_WORMHOLE_MUSIC, SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    public static boolean isPlaying() {
        return current != null && !current.isStopped()
                && Minecraft.getInstance().getSoundManager().isActive(current);
    }

    public static boolean isPlayingForRide() {
        return isPlaying() && !current.fading;
    }

    public static UltraWormholeMusic current() {
        return current;
    }

    public static void start() {
        if (isPlayingForRide()) return;
        if (isPlaying()) {
            current.fading = current.rideSeen = false;
            current.ticks = 0;
            return;
        }
        if (current != null) current.stop();
        current = new UltraWormholeMusic();
        Minecraft.getInstance().getSoundManager().play(current);
        UltraWorldMusic.holdReactiveMusic(current);
    }

    /** Stops vanilla's song for good, once it can't be heard under the white any more. */
    public static void stopVanilla() {
        if (current == null || current.vanillaOff) return;
        current.vanillaOff = true;
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    public static void fadeOut() {
        if (current != null) current.fading = true;
    }

    public static void stopNow() {
        if (current != null) current.stop();
        current = null;
    }

    @Override
    public void tick() {
        ticks++;
        if (RideClient.isActive()) rideSeen = true;
        else if (!rideSeen && ticks > 200) fading = true;
        if (ticks > VANILLA_OFF_TICKS && !fading) stopVanilla();

        if (!fading) {
            level = Math.min(1f, level + 1 / BACK_TICKS);
        } else if (RideClient.isCovered() && !UltraWorldMusic.isPlayingHere()) {
            level = Math.max(Math.min(level, DUCKED), level - 1 / DUCK_TICKS);
        } else {
            level -= 1 / FADE_TICKS;
        }
        volume = level <= 0 ? 0 : level * level * (3 - 2 * level);
        if (level <= 0) {
            stop();
            if (current == this) current = null;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}

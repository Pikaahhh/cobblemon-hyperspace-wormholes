package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Ultra Wormhole theme. Starts as you touch a wormhole, loops through the ride on the music channel, keeps vanilla
 * music quiet, and fades out with the white flash on the way back.
 */
public final class UltraWormholeMusic extends AbstractTickableSoundInstance {
    private static UltraWormholeMusic current;

    private boolean fading;
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
        if (current != null) current.stop();
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        current = new UltraWormholeMusic();
        mc.getSoundManager().play(current);
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

        if (fading) {
            volume = Math.min(volume, RideClient.flash(1f));
            if (volume <= 0.001f) {
                stop();
                if (current == this) current = null;
            }
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}

package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.mojang.blaze3d.audio.Channel;
import dev.hyperspacewormholes.hyperspace.mixin.SoundEngineAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.SoundManagerAccessor;
import dev.hyperspacewormholes.wormholes.client.SeamlessLoopStream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/**
 * Plays the Hyperspace music while you're there, keeps it going through battles and turns Reactive Music down
 * meanwhile. Songs blend into the next over BLEND_TICKS and are levelled to the quietest (MIXES, -17.9 LUFS); ones with
 * loop points (SeamlessLoopStream) play their intro and loop, then the loop once more. When the Hoopa event's glitch
 * starts (HoopaEvent.Glitch, through HoopaGlitch) the music tears up, dropping out and sagging in pitch until silent,
 * and stays that way until Glitch(0) or you leave.
 */
public class HyperspaceMusic {
    private static final int PASSES = 2, BLEND_TICKS = 120, TEAR_TICKS = 3;
    private static final Mix UNKNOWN = new Mix(0.75f, 0);
    /** Each song's volume, and how long it is in seconds if it has no loop points. */
    private static final Map<String, Mix> MIXES = Map.of(
            "hyperspace_wormholes:sounds/music/hyperspace.ogg", new Mix(0.75f, 203),
            "hyperspace_wormholes:sounds/music/dream_az.ogg", new Mix(0.6f, 212),
            "hyperspace_wormholes:sounds/music/za/hyperspace_lumiose_field.ogg", new Mix(0.7f, 0),
            "hyperspace_wormholes:sounds/music/za/hyperspace_lumiose_field_2.ogg", new Mix(0.51f, 0),
            "hyperspace_wormholes:sounds/music/za/hyperspace_special_field.ogg", new Mix(1f, 0));
    private static Track playing, leaving;
    private static int song = -1;
    private static boolean quietened, silent;

    private record Mix(float volume, int seconds) {
    }

    private static class Track extends AbstractTickableSoundInstance {
        private final float level;
        private int age, fadeAt = -1, glitchLeft, glitchLength;
        private float glitchFrom;
        private boolean dropped;

        Track(SoundEvent sound, SoundManager manager) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            relative = true;
            attenuation = Attenuation.NONE;
            resolve(manager);
            String file = this.sound == null ? "" : this.sound.getPath().toString();
            Mix mix = MIXES.getOrDefault(file, UNKNOWN);
            level = mix.volume();
            volume = level / BLEND_TICKS;
            SeamlessLoopStream.Loop loop = this.sound == null ? null : SeamlessLoopStream.loopFor(this.sound.getPath());
            if (loop != null) {
                looping = true;
                delay = 0;
                double samples = loop.end() + (PASSES - 1) * (double) (loop.end() - loop.start());
                fadeAt = (int) Math.round(samples / loop.rate() * 20);
            } else if (mix.seconds() > 0) {
                fadeAt = mix.seconds() * 20 - BLEND_TICKS;
            }
        }

        @Override
        public void tick() {
            age++;
            if (glitchLength > 0) {
                tear();
                return;
            }
            float blend = Math.min(1f, age / (float) BLEND_TICKS);
            if (fadeAt >= 0 && age >= fadeAt) blend = Math.min(blend, 1f - (age - fadeAt) / (float) BLEND_TICKS);
            volume = level * Mth.sin(Math.max(0f, blend) * Mth.HALF_PI);
            if (fadeAt >= 0 && age >= fadeAt + BLEND_TICKS) stop();
        }

        /** A new tear every TEAR_TICKS: more of them silent and the pitch further off, sagging as it dies. */
        private void tear() {
            float t = 1f - glitchLeft / (float) glitchLength;
            if (glitchLeft % TEAR_TICKS == 0) {
                dropped = random.nextFloat() < 0.15f + 0.6f * t;
                pitch = Mth.clamp(1f - 0.5f * t + (random.nextFloat() - 0.5f) * (0.1f + 0.8f * t), 0.5f, 1.5f);
            }
            volume = dropped ? 0f : glitchFrom * (1f - t * t);
            if (--glitchLeft <= 0) stop();
        }

        void glitch(int ticks) {
            if (ticks <= 0 || glitchLength > 0) return;
            glitchLeft = glitchLength = ticks;
            glitchFrom = volume;
        }

        boolean blending() {
            return fadeAt >= 0 && age >= fadeAt;
        }

        void end() {
            stop();
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HyperspaceMusic::tick);
    }

    /**
     * From HoopaEvent.Glitch: tears the music down over this many ticks and holds the silence; 0 lets it play again.
     */
    static void glitch(int ticks) {
        silent = ticks > 0;
        if (playing != null) playing.glitch(ticks);
        if (leaving != null) leaving.glitch(ticks);
    }

    private static void tick(Minecraft client) {
        boolean inside = client.level != null && client.level.dimension().equals(Hyperspace.HYPERSPACE);
        if (!inside) {
            end(client, playing);
            end(client, leaving);
            playing = null;
            leaving = null;
            silent = false;
            song = -1;
            return;
        }
        if (!quietened) {
            quietened = true;
            quietenReactiveMusic();
        }
        client.getMusicManager().stopPlaying();
        if (leaving != null && !client.getSoundManager().isActive(leaving)) leaving = null;
        if (silent) return;
        if (playing != null && playing.blending() && leaving == null) {
            leaving = playing;
            playing = null;
        }
        if (playing == null || !client.getSoundManager().isActive(playing)) {
            List<SoundEvent> songs = ModSounds.HYPERSPACE_SONGS;
            song = song < 0 ? client.level.getRandom().nextInt(songs.size()) : (song + 1) % songs.size();
            playing = new Track(songs.get(song), client.getSoundManager());
            client.getSoundManager().play(playing);
            return;
        }
        if (CobblemonClient.INSTANCE.getBattle() != null) {
            keepRunning(client, playing);
            if (leaving != null) keepRunning(client, leaving);
        }
    }

    private static void end(Minecraft client, Track track) {
        if (track == null) return;
        track.end();
        client.getSoundManager().stop(track);
    }

    private static void keepRunning(Minecraft client, Track track) {
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccessor) ((SoundManagerAccessor) client.getSoundManager())
                .hyperspace_wormholes$getSoundEngine()).hyperspace_wormholes$getInstanceToChannel().get(track);
        if (handle != null) handle.execute(Channel::unpause);
    }

    @SuppressWarnings("unchecked")
    private static void quietenReactiveMusic() {
        try {
            Object config = Class.forName("circuitlord.reactivemusic.ReactiveMusic").getField("config").get(null);
            List<String> blacklist = (List<String>) config.getClass().getField("blacklistedDimensions").get(config);
            String id = Hyperspace.HYPERSPACE.location().toString();
            if (!blacklist.contains(id)) blacklist.add(id);
        } catch (Throwable ignored) {
        }
    }
}

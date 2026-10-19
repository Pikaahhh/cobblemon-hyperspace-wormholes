package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.mojang.blaze3d.audio.Channel;
import dev.hyperspacewormholes.hyperspace.mixin.SoundEngineAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.SoundManagerAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Plays the Hyperspace music while you're there, keeps it going through battles and turns Reactive Music down in the
 * meantime.
 */
public class HyperspaceMusic {
    private static final float VOLUME = 0.75f;
    private static Track playing;
    private static int song = -1;
    private static boolean quietened;

    private static class Track extends AbstractTickableSoundInstance {
        Track(SoundEvent sound) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            volume = VOLUME;
            relative = true;
            attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
        }

        void end() {
            stop();
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HyperspaceMusic::tick);
    }

    private static void tick(Minecraft client) {
        boolean inside = client.level != null && client.level.dimension().equals(Hyperspace.HYPERSPACE);
        if (!inside) {
            if (playing != null) {
                playing.end();
                client.getSoundManager().stop(playing);
                playing = null;
            }
            song = -1;
            return;
        }
        if (!quietened) {
            quietened = true;
            quietenReactiveMusic();
        }
        client.getMusicManager().stopPlaying();
        if (playing == null || !client.getSoundManager().isActive(playing)) {
            List<SoundEvent> songs = ModSounds.HYPERSPACE_SONGS;
            song = song < 0 ? client.level.getRandom().nextInt(songs.size()) : (song + 1) % songs.size();
            playing = new Track(songs.get(song));
            client.getSoundManager().play(playing);
            return;
        }
        if (CobblemonClient.INSTANCE.getBattle() != null) keepRunning(client);
    }

    private static void keepRunning(Minecraft client) {
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccessor) ((SoundManagerAccessor) client.getSoundManager())
                .hyperspace_wormholes$getSoundEngine()).hyperspace_wormholes$getInstanceToChannel().get(playing);
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

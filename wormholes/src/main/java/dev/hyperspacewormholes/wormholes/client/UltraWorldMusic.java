package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Plays the theme of each Ultra world that has its own music from the mod itself, since Reactive Music takes over
 * vanilla's music player and biome music never played. The theme loops while you're in its world (an INTRO file beside it
 * plays once first) and fades out over 2 s when you leave or a ride starts. It is held back during boss fights and
 * Necrozma's cutscenes, then fades in as the battle music fades out.
 */
public final class UltraWorldMusic extends AbstractTickableSoundInstance implements FabricSoundInstance {
    private static final String INTRO = "_intro.ogg";
    private static final int RETRY_TICKS = 40;
    private static final float FADE_PER_TICK = 0.025f;
    private static final int AFTER_HOLD = 20, FIGHT_HOLD = 200;
    private static final double FIGHT_RANGE = 64;
    private static final Set<String> FUSED = Set.of("dusk-fusion", "dawn-fusion", "ultra-fusion");
    private static @Nullable UltraWorldMusic current;
    private static int wait, sinceHold = Integer.MAX_VALUE;
    private static @Nullable Method reactiveHold;
    private static boolean reactiveLooked;

    private boolean fading;

    private UltraWorldMusic(SoundEvent theme, boolean fadeIn) {
        super(theme, SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        if (fadeIn) this.volume = 0;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(UltraWorldMusic::update);
    }

    public static boolean isPlaying() {
        return current != null && Minecraft.getInstance().getSoundManager().isActive(current);
    }

    private static void update(Minecraft mc) {
        if (wait > 0) wait--;
        if (mc.player == null || mc.level == null) {
            sinceHold = Integer.MAX_VALUE;
        } else if (CobblemonClient.INSTANCE.getBattle() != null && ownBattleMusic(mc) || Cinematics.isShowing()
                || NecrozmaWeakened.holdsDown()) {
            sinceHold = 0;
        } else if (sinceHold < Integer.MAX_VALUE) {
            sinceHold++;
        }
        boolean held = sinceHold < AFTER_HOLD || sinceHold < FIGHT_HOLD && near(mc, FUSED);
        if (held && current != null) {
            mc.getSoundManager().stop(current);
            current = null;
        }
        SoundEvent theme = mc.player == null || mc.level == null || UltraWormholeMusic.isPlaying() || held ? null : themeAt(mc);
        if (current != null && !mc.getSoundManager().isActive(current)) current = null;
        if (current != null) {
            current.fading = theme == null || !theme.getLocation().equals(current.getLocation());
            return;
        }
        if (theme == null || wait > 0) return;
        wait = RETRY_TICKS;
        mc.getMusicManager().stopPlaying();
        if (sinceHold < Integer.MAX_VALUE) Wormholes.LOGGER.info("[music] {} fades back in {} ticks after the last hold", theme.getLocation(), sinceHold);
        current = new UltraWorldMusic(theme, sinceHold < Integer.MAX_VALUE);
        sinceHold = Integer.MAX_VALUE;
        mc.getSoundManager().play(current);
        holdReactiveMusic(current);
    }

    private static boolean near(Minecraft mc, Set<String> forms) {
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(FIGHT_RANGE))) {
            if (entity instanceof PokemonEntity pokemon && pokemon.getOwner() == null
                    && pokemon.getEntityData().get(PokemonEntity.getASPECTS()).stream().anyMatch(forms::contains)) return true;
        }
        return false;
    }

    private static boolean ownBattleMusic(Minecraft mc) {
        BattleMusicInstance music = BattleMusicController.INSTANCE.getMusic();
        return !music.isFading() && mc.getSoundManager().isActive(music);
    }

    public static boolean keepsThemeOver(SoundInstance battleMusic) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !UltraWormholeMusic.isPlaying()
                && !battleMusic.getLocation().getNamespace().equals(Wormholes.MOD_ID) && themeAt(mc) != null;
    }

    private static @Nullable SoundEvent themeAt(Minecraft mc) {
        Music music = mc.level.getBiome(mc.player.blockPosition()).value().getBackgroundMusic().orElse(null);
        if (music == null) return null;
        SoundEvent theme = music.getEvent().value();
        return theme.getLocation().getNamespace().equals(Wormholes.MOD_ID) ? theme : null;
    }

    public static void holdReactiveMusic(SoundInstance sound) {
        if (!reactiveLooked) {
            reactiveLooked = true;
            if (FabricLoader.getInstance().isModLoaded("reactivemusic")) {
                try {
                    reactiveHold = Class.forName("circuitlord.reactivemusic.ReactiveMusic")
                            .getMethod("trackSoundMuteMusic", SoundInstance.class, boolean.class);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Wormholes.LOGGER.warn("Couldn't find Reactive Music's music hold", e);
                }
            }
        }
        if (reactiveHold == null) return;
        try {
            reactiveHold.invoke(null, sound, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.warn("Couldn't ask Reactive Music to hold its music", e);
            reactiveHold = null;
        }
    }

    @Override
    public void tick() {
        if (!fading) {
            volume = Math.min(1f, volume + FADE_PER_TICK);
            return;
        }
        volume -= FADE_PER_TICK;
        if (volume <= 0.001f) stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
        ResourceLocation intro = id.withPath(path -> path.replace(".ogg", INTRO));
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (resources.getResource(intro).isEmpty()) return loader.getStream(id, repeatInstantly);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new IntroThenLoop(new JOrbisAudioStream(resources.open(intro)),
                        new LoopingAudioStream(JOrbisAudioStream::new, resources.open(id)));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    private static final class IntroThenLoop implements AudioStream {
        private final AudioStream body;
        private @Nullable AudioStream intro;

        private IntroThenLoop(AudioStream intro, AudioStream body) {
            this.intro = intro;
            this.body = body;
        }

        @Override
        public AudioFormat getFormat() {
            return intro != null ? intro.getFormat() : body.getFormat();
        }

        @Override
        public ByteBuffer read(int expectedSize) throws IOException {
            if (intro != null) {
                ByteBuffer data = intro.read(expectedSize);
                if (data.hasRemaining()) return data;
                intro.close();
                intro = null;
            }
            return body.read(expectedSize);
        }

        @Override
        public void close() throws IOException {
            if (intro != null) intro.close();
            body.close();
        }
    }
}

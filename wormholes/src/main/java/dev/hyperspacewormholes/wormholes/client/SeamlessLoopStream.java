package dev.hyperspacewormholes.wormholes.client;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChunkedSampleByteBuf;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * A looping music stream that plays its file from the start once and then repeats only the part between the track's
 * loop points, sample-exact, like Ultra Sun and Ultra Moon loop their battle music. BossMusicLoopMixin hands it to any
 * looping sound whose file is in LOOPS, since vanilla's LoopingAudioStream would replay the whole file.
 * At the seam, pass one runs FADE frames on into a copy of the loop start while the next pass fades in, so the two
 * places' slightly different Vorbis coding never meets as a step.
 */
public final class SeamlessLoopStream implements AudioStream {
    private static final Logger LOGGER = LoggerFactory.getLogger("hyperspace_wormholes");
    private static final int FADE = 256;
    private static final int MAX_RESTARTS_PER_READ = 4;
    private static final Map<String, Loop> LOOPS = Map.of(
            "hyperspace_wormholes:sounds/music/usum/ultra_necrozma_battle.ogg", new Loop(516096, 3233405, FADE, 32728),
            "hyperspace_wormholes:sounds/music/usum/necrozma_fused_battle.ogg", new Loop(14336, 2732000, FADE, 32728),
            "hyperspace_wormholes:sounds/music/za/hoopa_unbound_battle.ogg", new Loop(1984000, 8896000, FADE, 48000),
            "hyperspace_wormholes:sounds/music/za/hyperspace_lumiose_field.ogg", new Loop(528000, 3792000, FADE, 48000),
            "hyperspace_wormholes:sounds/music/za/hyperspace_lumiose_field_2.ogg", new Loop(0, 4464000, FADE, 48000),
            "hyperspace_wormholes:sounds/music/za/hyperspace_special_field.ogg", new Loop(288320, 1872320, FADE, 48000));

    public record Loop(long start, long end, int fade, int rate) {}

    private final BufferedInputStream input;
    private final Loop loop;
    private final int channels;
    private final float[] tail;
    private JOrbisAudioStream decoder;
    private long position;
    private boolean repeating;
    private @Nullable ChunkedSampleByteBuf out;

    public SeamlessLoopStream(InputStream in, Loop loop) throws IOException {
        this.input = new BufferedInputStream(in);
        this.input.mark(Integer.MAX_VALUE);
        this.decoder = new JOrbisAudioStream(unclosed());
        this.channels = decoder.getFormat().getChannels();
        int rate = Math.round(decoder.getFormat().getSampleRate());
        if (rate != loop.rate()) {
            LOGGER.warn("[music] {} Hz file where its loop points are for {} Hz: looping the whole file instead",
                    rate, loop.rate());
            loop = new Loop(0, Long.MAX_VALUE / 4, 0, rate);
        }
        this.loop = loop;
        this.tail = new float[loop.fade() * channels];
    }

    public static @Nullable Loop loopFor(ResourceLocation file) {
        return LOOPS.get(file.toString());
    }

    @Override
    public AudioFormat getFormat() {
        return decoder.getFormat();
    }

    @Override
    public ByteBuffer read(int expectedSize) throws IOException {
        ChunkedSampleByteBuf buffer = new ChunkedSampleByteBuf(expectedSize + 8192);
        out = buffer;
        int restarts = 0;
        try {
            while (buffer.size() < expectedSize) {
                boolean more = decoder.readChunk(this::route);
                if (!more || position >= (loop.end() + loop.fade()) * channels) {
                    if (++restarts > MAX_RESTARTS_PER_READ) break;
                    restart();
                }
            }
        } finally {
            out = null;
        }
        return buffer.get();
    }

    private void route(float sample) {
        long p = position++;
        long frame = p / channels;
        if (frame >= loop.end()) {
            if (frame < loop.end() + loop.fade()) tail[(int) (p - loop.end() * channels)] = sample;
            return;
        }
        if (!repeating) {
            out.accept(sample);
            return;
        }
        if (frame < loop.start()) return;
        long into = frame - loop.start();
        if (into < loop.fade()) {
            float w = (into + 0.5f) / loop.fade();
            out.accept(tail[(int) (p - loop.start() * channels)] * (1 - w) + sample * w);
        } else {
            out.accept(sample);
        }
    }

    private void restart() throws IOException {
        decoder.close();
        input.reset();
        decoder = new JOrbisAudioStream(unclosed());
        position = 0;
        repeating = true;
    }

    private InputStream unclosed() {
        return new FilterInputStream(input) {
            @Override
            public void close() {
            }
        };
    }

    @Override
    public void close() throws IOException {
        decoder.close();
        input.close();
    }
}

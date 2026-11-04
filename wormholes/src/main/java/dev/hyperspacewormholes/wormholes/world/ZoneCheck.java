package dev.hyperspacewormholes.wormholes.world;

import dev.hyperspacewormholes.wormholes.Wormholes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Behind -Dhyperspace_wormholes.zoneCheck=true, logs one stable hash of each zone and crater formation as it loads, with
 * its load time, so two builds can be compared in a real run: everything the loaded zone holds goes through SHA-256.
 * It also logs what building zone tiles into chunks has cost so far, every PLACED_EVERY tiles.
 */
final class ZoneCheck {
    private static final boolean ON = Boolean.getBoolean("hyperspace_wormholes.zoneCheck");
    private static final int PLACED_EVERY = 256;
    private static final LongAdder PLACED_NANOS = new LongAdder();
    private static final AtomicLong PLACED = new AtomicLong();

    interface Content {
        void write(DataOutputStream out) throws IOException;
    }

    private ZoneCheck() {}

    static void placed(long began) {
        if (!ON) return;
        PLACED_NANOS.add(System.nanoTime() - began);
        long count = PLACED.incrementAndGet();
        if (count % PLACED_EVERY == 0) Wormholes.LOGGER.info("zoneCheck placed {} tiles into chunks in {} ms", count, PLACED_NANOS.sum() / 1_000_000);
    }

    static void log(String kind, String name, long began, Content content) {
        if (!ON) return;
        long millis = (System.nanoTime() - began) / 1_000_000;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), sha))) {
                content.write(out);
            }
            Wormholes.LOGGER.info("zoneCheck {} {} {} ({} ms)", kind, name, HexFormat.of().formatHex(sha.digest(), 0, 8), millis);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

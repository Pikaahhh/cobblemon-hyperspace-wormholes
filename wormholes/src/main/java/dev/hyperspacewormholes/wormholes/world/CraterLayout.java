package dev.hyperspacewormholes.wormholes.world;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.hyperspacewormholes.wormholes.world.CellFeature.cellHash;
import static dev.hyperspacewormholes.wormholes.world.CellFeature.mix;
import static dev.hyperspacewormholes.wormholes.world.CellFeature.unit;

/**
 * Where the Ultra Crater's plateaus and the craters between them stand, from the world seed: one plateau (the game's
 * crater cluster) per BIG_CELL square, BIG_CHANCE of the time, and one smaller crater (piece_1..16) per CRATER_CELL
 * square at a hashed spot, trying up to CRATER_ATTEMPTS spots to stay CLEAR of a plateau. The playable plateau is the
 * zone crater_283, repeated every ZONE_REPEAT from ZONE_X, ZONE_Z (keep in step with the zone file), and no other
 * plateau stands within ZONE_ROOM of it. giants picks the craters near it where CraterGiants stands a giant Celesteela.
 */
public final class CraterLayout {
    static final int BIG_CELL = 576, BIG_MARGIN = 160, BIG_CHANCE = 50, SMALL_PLATEAU_CHANCE = 30;
    static final int CRATER_CELL = 62, CRATER_EMPTY = 20, CRATER_ATTEMPTS = 3, CLEAR = 3, PIECES = 11;
    static final int ZONE_X = 130, ZONE_Z = 113, ZONE_REPEAT = 10_000, ZONE_ROOM = 640;
    static final int GIANT_NEAR = 300, GIANT_ONE_IN = 3, GIANTS = 5;
    private static final String FOLDER = "crater_formations";

    static final List<BlockTemplate> PLATEAUS = Stream.of("plateau", "plateau_small").map(n -> BlockTemplate.load(FOLDER, n)).toList();
    static final List<BlockTemplate> CRATERS = IntStream.rangeClosed(1, PIECES).mapToObj(i -> BlockTemplate.load(FOLDER, "piece_" + i)).toList();
    static final int CRATER_REACH = CRATERS.stream().mapToInt(BlockTemplate::reach).max().orElse(0) + 1;
    private static final double[] CRATER_ODDS = odds();
    private static final List<Footprint> FOOTPRINTS = PLATEAUS.stream().map(Footprint::of).toList();
    private static final Map<Long, CraterLayout> LAYOUTS = new ConcurrentHashMap<>();

    private final long seed;
    private final Map<Long, Optional<Spot>> craters = new ConcurrentHashMap<>();

    private CraterLayout(long seed) {
        this.seed = seed;
    }

    static CraterLayout of(long seed) {
        if (LAYOUTS.size() > 16) LAYOUTS.clear();
        return LAYOUTS.computeIfAbsent(seed, CraterLayout::new);
    }

    long seed() {
        return seed;
    }

    public record Spot(BlockTemplate template, int kind, int x, int z, int turns, boolean mirror) {
        boolean over(int wx, int wz) {
            int c = template.column(wx, wz, x, z, turns, mirror);
            return c >= 0 && template.filled(c);
        }
    }

    private record Footprint(int sx, int sz, int ox, int oz, float[] distance) {
        static Footprint of(BlockTemplate t) {
            int pad = CLEAR + 2, sx = t.sx() + 2 * pad, sz = t.sz() + 2 * pad;
            float[] d = new float[sx * sz];
            Arrays.fill(d, Float.MAX_VALUE);
            for (int x = 0; x < t.sx(); x++) {
                for (int z = 0; z < t.sz(); z++) {
                    if (t.filled(x * t.sz() + z)) d[(x + pad) * sz + z + pad] = 0;
                }
            }
            float diagonal = (float) Math.sqrt(2);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < sx; i++) {
                    int x = pass == 0 ? i : sx - 1 - i;
                    for (int j = 0; j < sz; j++) {
                        int z = pass == 0 ? j : sz - 1 - j, step = pass == 0 ? -1 : 1;
                        float best = d[x * sz + z];
                        if (x + step >= 0 && x + step < sx) best = Math.min(best, d[(x + step) * sz + z] + 1);
                        if (z + step >= 0 && z + step < sz) best = Math.min(best, d[x * sz + z + step] + 1);
                        if (x + step >= 0 && x + step < sx && z + step >= 0 && z + step < sz) best = Math.min(best, d[(x + step) * sz + z + step] + diagonal);
                        if (x + step >= 0 && x + step < sx && z - step >= 0 && z - step < sz) best = Math.min(best, d[(x + step) * sz + z - step] + diagonal);
                        d[x * sz + z] = best;
                    }
                }
            }
            return new Footprint(sx, sz, t.ox() + pad, t.oz() + pad, d);
        }
    }

    private static double[] odds() {
        double[] odds = new double[CRATERS.size()];
        double total = 0;
        for (int i = 0; i < odds.length; i++) {
            BlockTemplate t = CRATERS.get(i);
            int area = 0;
            for (int c = 0; c < t.sx() * t.sz(); c++) {
                if (t.filled(c)) area++;
            }
            total += 1 / Math.sqrt(Math.max(1, area));
            odds[i] = total;
        }
        for (int i = 0; i < odds.length; i++) odds[i] /= total;
        return odds;
    }

    public static List<Spot> giants(long seed, Spot zone) {
        CraterLayout layout = of(seed);
        List<Spot> found = new ArrayList<>();
        int r = GIANT_NEAR / CRATER_CELL + 1;
        int cx = Math.floorDiv(zone.x(), CRATER_CELL), cz = Math.floorDiv(zone.z(), CRATER_CELL);
        for (int i = cx - r; i <= cx + r; i++) {
            for (int j = cz - r; j <= cz + r; j++) {
                Spot crater = layout.crater(i, j);
                if (crater == null || Math.hypot(crater.x() - zone.x(), crater.z() - zone.z()) > GIANT_NEAR) continue;
                if (unit(cellHash(seed, i, j, 0x61A47L)) * GIANT_ONE_IN < 1) found.add(crater);
            }
        }
        found.sort(Comparator.comparingDouble(s -> Math.hypot(s.x() - zone.x(), s.z() - zone.z())));
        return found.subList(0, Math.min(GIANTS, found.size()));
    }

    public static Spot zone(int x, int z) {
        int zx = ZONE_X + (int) Math.round((x - ZONE_X) / (double) ZONE_REPEAT) * ZONE_REPEAT;
        int zz = ZONE_Z + (int) Math.round((z - ZONE_Z) / (double) ZONE_REPEAT) * ZONE_REPEAT;
        return new Spot(PLATEAUS.get(0), 0, zx, zz, 0, false);
    }

    @Nullable Spot plateau(int cx, int cz) {
        long h = cellHash(seed, cx, cz, 0x7A11E7L);
        if (unit(mix(h + 1)) * 100 >= BIG_CHANCE) return null;
        int kind = unit(mix(h + 2)) * 100 < SMALL_PLATEAU_CHANCE ? 1 : 0;
        int x = cx * BIG_CELL + BIG_MARGIN + (int) (unit(mix(h + 3)) * (BIG_CELL - 2 * BIG_MARGIN));
        int z = cz * BIG_CELL + BIG_MARGIN + (int) (unit(mix(h + 4)) * (BIG_CELL - 2 * BIG_MARGIN));
        Spot zone = zone(x, z);
        if (Math.hypot(x - zone.x(), z - zone.z()) < ZONE_ROOM) return null;
        return new Spot(PLATEAUS.get(kind), kind, x, z, (int) (unit(mix(h + 5)) * 4), unit(mix(h + 6)) < 0.5);
    }

    float plateauDistance(int x, int z) {
        Spot zone = zone(x, z);
        Footprint zf = FOOTPRINTS.get(0);
        int zk = BlockTemplate.local(x, z, zone.x(), zone.z(), 0, false, zf.ox(), zf.oz(), zf.sx(), zf.sz());
        float best = zk >= 0 ? zf.distance()[zk] : Float.MAX_VALUE;
        int cx = Math.floorDiv(x, BIG_CELL), cz = Math.floorDiv(z, BIG_CELL);
        for (int i = cx - 1; i <= cx + 1; i++) {
            for (int j = cz - 1; j <= cz + 1; j++) {
                Spot p = plateau(i, j);
                if (p == null) continue;
                Footprint f = FOOTPRINTS.get(p.kind());
                int k = BlockTemplate.local(x, z, p.x(), p.z(), p.turns(), p.mirror(), f.ox(), f.oz(), f.sx(), f.sz());
                if (k >= 0) best = Math.min(best, f.distance()[k]);
            }
        }
        return best;
    }

    @Nullable Spot crater(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Optional<Spot> known = craters.get(key);
        if (known != null) return known.orElse(null);
        Spot spot = pick(cx, cz);
        if (craters.size() > 100_000) craters.clear();
        craters.putIfAbsent(key, Optional.ofNullable(spot));
        return spot;
    }

    private @Nullable Spot pick(int cx, int cz) {
        long cell = cellHash(seed, cx, cz, 0xC4A7E5L);
        if (unit(mix(cell + 1)) * 100 < CRATER_EMPTY) return null;
        for (int attempt = 0; attempt < CRATER_ATTEMPTS; attempt++) {
            long h = mix(cell + 31L * (attempt + 1));
            double roll = unit(mix(h + 1));
            int kind = 0;
            while (kind < CRATER_ODDS.length - 1 && roll > CRATER_ODDS[kind]) kind++;
            int x = cx * CRATER_CELL + (int) (unit(mix(h + 2)) * CRATER_CELL);
            int z = cz * CRATER_CELL + (int) (unit(mix(h + 3)) * CRATER_CELL);
            Spot spot = new Spot(CRATERS.get(kind), kind, x, z, (int) (unit(mix(h + 4)) * 4), unit(mix(h + 5)) < 0.5);
            if (clearOfPlateaus(spot)) return spot;
        }
        return null;
    }

    private boolean clearOfPlateaus(Spot spot) {
        int r = spot.template().reach();
        for (int x = spot.x() - r; x <= spot.x() + r; x += 2) {
            for (int z = spot.z() - r; z <= spot.z() + r; z += 2) {
                if (spot.over(x, z) && plateauDistance(x, z) <= CLEAR) return false;
            }
        }
        return true;
    }

    boolean onCrater(int x, int z) {
        int r = (CRATER_REACH + CRATER_CELL - 1) / CRATER_CELL;
        int cx = Math.floorDiv(x, CRATER_CELL), cz = Math.floorDiv(z, CRATER_CELL);
        for (int i = cx - r; i <= cx + r; i++) {
            for (int j = cz - r; j <= cz + r; j++) {
                Spot spot = crater(i, j);
                if (spot != null && spot.over(x, z)) return true;
            }
        }
        return false;
    }
}

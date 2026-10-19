package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything else on the Ultra Valley besides craters: young Celesteela spires, arching black roots and steam vents.
 */
public class UltraCraterSpireFeature extends CellFeature {
    private static final int SPIRE_CHANCE = 40;
    private static final int VENT_CHANCE = 60;
    private static final int SPOT_TRIES = 10;

    private static final BlockState[] LATTICE = {Blocks.RED_NETHER_BRICKS.defaultBlockState(),
            Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState()};
    private static final BlockState MOUND = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState MOUND_DARK = Blocks.MUD_BRICKS.defaultBlockState();
    private static final BlockState ROOT = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState ROOT_SMOOTH = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

    public UltraCraterSpireFeature() {
        super(36, 8, 28, 0x5F17EL);
    }

    @Override
    protected @Nullable Map<BlockPos, BlockState> shape(RandomSource random, int x, int z, Ground ground) {
        Map<BlockPos, BlockState> into = new HashMap<>();
        if (random.nextInt(100) < SPIRE_CHANCE) {
            BlockPos spot = offCrater(random, x, z, 6, ground);
            if (spot != null) spire(random, spot, into);
        }
        int roots = random.nextInt(4);
        for (int i = 0; i < roots; i++) root(random, x + random.nextInt(25) - 12, z + random.nextInt(25) - 12, ground, into);
        if (random.nextInt(100) < VENT_CHANCE) {
            BlockPos spot = offCrater(random, x, z, 14, ground);
            if (spot != null) into.put(spot.below(), SteamVent.BLOCK.defaultBlockState());
        }
        return into.isEmpty() ? null : into;
    }

    private static @Nullable BlockPos offCrater(RandomSource random, int x, int z, int spread, Ground ground) {
        for (int i = 0; i < SPOT_TRIES; i++) {
            int sx = x + random.nextInt(spread * 2 + 1) - spread, sz = z + random.nextInt(spread * 2 + 1) - spread;
            if (!ground.dry(sx, sz) || UltraCraterFeature.top(ground, sx, sz) != null) continue;
            return new BlockPos(sx, UltraCraterFeature.surface(ground, sx, sz), sz);
        }
        return null;
    }

    private static void spire(RandomSource random, BlockPos at, Map<BlockPos, BlockState> into) {
        Vec3 foot = Vec3.atBottomCenterOf(at).add(0, -2, 0);
        Function<BlockPos, BlockState> mound = p -> (mix(p) & 3) == 0 ? MOUND_DARK : MOUND;
        Sculpt.cone(foot, 4.5 + random.nextDouble() * 1.5, 4, 1.5, mound, into);
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5 + random.nextDouble() * 0.6;
            Vec3 end = foot.add(Math.cos(angle) * 6.5, 0.5, Math.sin(angle) * 6.5);
            Sculpt.tube(foot.add(0, 2.5, 0), end, 1.6, 0.9, mound, into);
        }
        Sculpt.cone(foot.add(0, 3, 0), 2.4, 16 + random.nextDouble() * 12, 0.2,
                p -> LATTICE[(int) ((mix(p) + Math.floorMod(p.getY(), 4)) % LATTICE.length)], into);
    }

    private static void root(RandomSource random, int x, int z, Ground ground, Map<BlockPos, BlockState> into) {
        if (!ground.dry(x, z)) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double span = 4 + random.nextDouble() * 5;
        double rise = 3 + random.nextDouble() * 3;
        boolean hook = random.nextInt(3) == 0;
        int ex = Mth.floor(x + Math.cos(angle) * span), ez = Mth.floor(z + Math.sin(angle) * span);
        Vec3 a = new Vec3(x + 0.5, UltraCraterFeature.surface(ground, x, z) - 1, z + 0.5);
        Vec3 b = new Vec3(ex + 0.5, UltraCraterFeature.surface(ground, ex, ez) - 1, ez + 0.5);
        double thick = 0.9 + random.nextDouble() * 0.4;
        Sculpt.arch(a, b, rise, 16, hook ? 0.8 : 1.0, t -> thick, p -> (mix(p) & 3) == 0 ? ROOT_SMOOTH : ROOT, into);
    }

    private static long mix(BlockPos p) {
        long h = p.asLong() * 0x9E3779B97F4A7C15L;
        return Math.abs(h ^ (h >>> 29));
    }
}

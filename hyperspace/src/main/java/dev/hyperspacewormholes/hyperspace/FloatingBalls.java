package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers the floating ball entities and scatters them around an island when a trip starts, spread out and on open
 * ground. Also places and clears the golden ball.
 */
public class FloatingBalls {
    public static final EntityType<FloatingBall> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "floating_poke_ball"),
            EntityType.Builder.<FloatingBall>of(FloatingBall::new, MobCategory.MISC)
                    .sized(FloatingBall.SIZE, FloatingBall.SIZE).fireImmune().clientTrackingRange(8).build("floating_poke_ball"));
    public static final EntityType<FloatingBall> GOLDEN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "golden_floating_poke_ball"),
            EntityType.Builder.<FloatingBall>of(FloatingBall::new, MobCategory.MISC)
                    .sized(FloatingBall.SIZE * FloatingBall.GOLDEN_SCALE, FloatingBall.SIZE * FloatingBall.GOLDEN_SCALE).fireImmune().clientTrackingRange(8)
                    .build("golden_floating_poke_ball"));

    public static final int PER_ZONE = 6;
    private static final int TRIES = 400;
    private static final double LIFT = 0.75;
    private static final double EDGE_GAP = 3;
    private static final double PORTAL_GAP = 5;
    private static final double APART = 6;
    private static final double CLEAR_REACH = 150;

    public static void register() {
    }

    public static void scatter(ServerLevel level, BlockPos centre, int slot, Zones.Zone zone) {
        List<Vec3> placed = new ArrayList<>();
        for (boolean strict : new boolean[]{true, false}) {
            for (int i = 0; i < TRIES && placed.size() < PER_ZONE; i++) {
                Vec3 pos = spot(level, centre, slot, zone, strict);
                if (pos == null || placed.stream().anyMatch(p -> p.distanceToSqr(pos) < APART * APART)) continue;
                spawn(level, TYPE.create(level), pos);
                placed.add(pos);
            }
        }
    }

    public static boolean golden(ServerLevel level, int island, UUID owner) {
        if (!Zones.isZone(island)) return false;
        for (boolean strict : new boolean[]{true, false}) {
            for (int i = 0; i < TRIES; i++) {
                Vec3 pos = spot(level, Zones.centre(island - Zones.BASE), island - Zones.BASE, Zones.get(island), strict);
                if (pos == null) continue;
                FloatingBall ball = GOLDEN.create(level);
                ball.setOwner(owner);
                return spawn(level, ball, pos);
            }
        }
        return false;
    }

    private static Vec3 spot(ServerLevel level, BlockPos centre, int slot, Zones.Zone zone, boolean strict) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(random.nextDouble()) * (Zones.edge(slot, angle) - EDGE_GAP);
        int x = centre.getX() + (int) Math.round(Math.cos(angle) * r);
        int z = centre.getZ() + (int) Math.round(Math.sin(angle) * r);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= level.getMinBuildHeight()) return null;
        BlockPos ground = new BlockPos(x, top - 1, z);
        if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty() || !open(level, ground, strict)) return null;
        Vec3 pos = new Vec3(x + 0.5, top + LIFT, z + 0.5);
        return near(pos, zone.ax, zone.az) || near(pos, zone.rx, zone.rz) ? null : pos;
    }

    private static boolean spawn(ServerLevel level, FloatingBall ball, Vec3 pos) {
        ball.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360f, 0f);
        return level.addFreshEntity(ball);
    }

    public static void clearGolden(ServerLevel level, int island) {
        BlockPos centre = Zones.centre(island - Zones.BASE);
        for (FloatingBall ball : level.getEntitiesOfClass(FloatingBall.class, new AABB(centre).inflate(CLEAR_REACH, 256, CLEAR_REACH))) {
            if (ball.golden()) ball.discard();
        }
    }

    private static boolean open(ServerLevel level, BlockPos ground, boolean strict) {
        for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, 1, -1), ground.offset(1, 3, 1))) {
            BlockState state = level.getBlockState(pos);
            if (strict ? !state.isAir() : !state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static boolean near(Vec3 pos, double x, double z) {
        double dx = pos.x - x, dz = pos.z - z;
        return dx * dx + dz * dz < PORTAL_GAP * PORTAL_GAP;
    }
}

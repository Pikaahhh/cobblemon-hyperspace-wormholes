package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where tunnel wormholes take you: the Overworld or the End, out along a line away from world spawn as far as you
 * flew. Finds and clears a landing and opens a way home.
 */
public final class WormholeDestinations {
    private static final int MIN_DISTANCE = 64;
    private static final double NEAR_SPAWN = 64;
    private static final int SEARCH_RANGE = 128;
    private static final int SEARCH_STEP = 8;
    private static final int WATER_SEARCH_RANGE = 256;
    private static final int WATER_RADIUS = 12;
    private static final int NETHER_SEARCH_TOP = 118;
    static final int RETURN_WORMHOLE_TICKS = 480 * 20;
    static final int WAYSTONE_RETURN_WORMHOLE_TICKS = 5 * 20;
    static final double EXIT_AIM_HEIGHT = 1.5;
    private static final int[] RETURN_BEHIND = {12, 10, 14, 8, 16, 18};
    private static final int EXIT_LIFT = 4;
    private static final int[] RETURN_SIDE = {0, 4, -4, 8, -8};
    private static final int RETURN_MAX_RISE = 6;
    private static final int RETURN_MAX_CLIMB = 24;
    private static final int RETURN_CLEAR_HALF_WIDTH = 2;
    private static final int RETURN_CLEAR_HEIGHT = 6;
    private static final int[] PLACEHOLDER_AHEAD = {9, 7, 11, 5, 13};
    private static final int[] PLACEHOLDER_SIDE = {0, 3, -3, 6, -6};
    private static final int FLOAT_CLEARANCE = 3;
    private static final int MAX_FLOAT = 24;

    private WormholeDestinations() {}

    public record Plan(ResourceKey<Level> dimension, int x, int z, int dirX, int dirZ, WormholeType type, int rings,
                       @Nullable String pokemon) {
        public float yaw() {
            return (float) (Mth.atan2(-dirX, dirZ) * Mth.RAD_TO_DEG);
        }
    }

    public record Arrival(ServerLevel level, Vec3 pos, float yaw, Plan plan) {}

    static Arrival arrivalAt(ServerLevel level, Vec3 pos, float yaw) {
        float fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int dirX = Math.abs(fx) > Math.abs(fz) ? (int) Math.signum(fx) : 0;
        int dirZ = dirX == 0 ? (fz < 0 ? -1 : 1) : 0;
        Plan plan = new Plan(level.dimension(), Mth.floor(pos.x), Mth.floor(pos.z), dirX, dirZ, WormholeType.BLUE, 0, null);
        return new Arrival(level, pos, yaw, plan);
    }

    static Plan plan(MinecraftServer server, WarpTunnel.Wormhole wormhole, long seed, double warpDistance,
                     @Nullable String override, @Nullable HomePoint start) {
        double roll = new Random(seed ^ (wormhole.index() * 0xC2B2AE3D27D4EB4FL)).nextDouble();
        ResourceKey<Level> dimension = switch (wormhole.type()) {
            case WHITE -> Level.END;
            case GOLD, GREEN -> roll < 0.85 ? Level.OVERWORLD : Level.END;
            default -> Level.OVERWORLD;
        };
        String pokemon = null;
        if (override != null) {
            pokemon = WormholePokemon.ultraBeastNamed(override, RandomSource.create(seed ^ wormhole.index()));
        } else if (wormhole.type() == WormholeType.WHITE) {
            pokemon = WormholePokemon.pick(WormholeType.WHITE, wormhole.rings(),
                    RandomSource.create(seed ^ (wormhole.index() * 0x5851F42D4C957F2DL)));
        }
        ResourceKey<Level> home = UltraBeastWorlds.homeOf(pokemon);
        if (home != null) dimension = home;
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            dimension = Level.OVERWORLD;
            level = server.overworld();
        }
        boolean fromStart = start != null && home == null;
        double ox = fromStart ? start.pos().x : 0, oz = fromStart ? start.pos().z : 0;
        BlockPos spawn = level.getSharedSpawnPos();
        double awayX = ox - spawn.getX(), awayZ = oz - spawn.getZ();
        boolean nearSpawn = !fromStart || awayX * awayX + awayZ * awayZ < NEAR_SPAWN * NEAR_SPAWN;
        List<int[]> axes = new ArrayList<>();
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (nearSpawn || dir[0] * awayX + dir[1] * awayZ > 0) axes.add(dir);
        }
        int[] dir = axes.get(Math.floorMod(Long.hashCode(seed * 31 + wormhole.index() * 0x9E3779B97F4A7C15L), axes.size()));

        int limit = (int) (level.getWorldBorder().getSize() / 2) - SEARCH_RANGE - 32;
        int distance = (int) Math.max(MIN_DISTANCE, Math.round(warpDistance));
        int x = (int) Mth.clamp(Math.round(ox + dir[0] * distance), -limit, limit);
        int z = (int) Mth.clamp(Math.round(oz + dir[1] * distance), -limit, limit);
        keepLoaded(level, x, z);
        return new Plan(dimension, x, z, dir[0], dir[1], wormhole.type(), wormhole.rings(), pokemon);
    }

    static Plan planUncharted(MinecraftServer server, @Nullable HomePoint start, double warpDistance, long seed) {
        ServerLevel level = start == null || start.dimension() == Level.NETHER ? server.overworld() : server.getLevel(start.dimension());
        if (level == null) level = server.overworld();
        Vec3 from = start != null ? start.pos() : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        double angle = new Random(seed ^ 0x5EEDFA11L).nextDouble() * Math.PI * 2;
        double dx = -Math.sin(angle), dz = Math.cos(angle);
        double distance = Math.max(MIN_DISTANCE, warpDistance);
        double limit = level.getWorldBorder().getSize() / 2 - SEARCH_RANGE - 32;
        int x = (int) Mth.clamp(Math.round(from.x + dx * distance), -limit, limit);
        int z = (int) Mth.clamp(Math.round(from.z + dz * distance), -limit, limit);
        int dirX = Math.abs(dx) > Math.abs(dz) ? (int) Math.signum(dx) : 0;
        int dirZ = dirX == 0 ? (dz < 0 ? -1 : 1) : 0;
        keepLoaded(level, x, z);
        return new Plan(level.dimension(), x, z, dirX, dirZ, WormholeType.GREEN, 0, null);
    }

    private static void keepLoaded(ServerLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, 64, z);
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
    }

    static Arrival resolve(MinecraftServer server, Plan plan) {
        ServerLevel level = server.getLevel(plan.dimension());
        if (level == null) level = server.overworld();
        boolean ceiling = level.dimensionType().hasCeiling();

        if (plan.type() == WormholeType.BLUE && !ceiling) {
            for (int i = 0; i <= WATER_SEARCH_RANGE / SEARCH_STEP * 2; i++) {
                int offset = (i + 1) / 2 * SEARCH_STEP * (i % 2 == 0 ? -1 : 1);
                int x = plan.x() + plan.dirX() * offset, z = plan.z() + plan.dirZ() * offset;
                level.getChunk(x >> 4, z >> 4);
                BlockPos found = surface(level, x, z, false);
                if (found != null && nearWater(level, found)) return arrival(level, found, plan);
            }
        }

        BlockPos fallback = null;
        for (int i = 0; i <= SEARCH_RANGE / SEARCH_STEP * 2; i++) {
            int offset = (i + 1) / 2 * SEARCH_STEP * (i % 2 == 0 ? -1 : 1);
            int x = plan.x() + plan.dirX() * offset, z = plan.z() + plan.dirZ() * offset;
            level.getChunk(x >> 4, z >> 4);
            BlockPos found = ceiling ? caveFloor(level, x, z, caveTop(level), level.getMinBuildHeight() + 2) : surface(level, x, z, false);
            if (found != null) return arrival(level, found, plan);
            if (fallback == null) fallback = surface(level, x, z, true);
        }
        return arrival(level, fallback != null ? fallback : naturalGroundNearCentre(level), plan);
    }

    static boolean nearWater(ServerLevel level, BlockPos feet) {
        for (int r = 3; r <= WATER_RADIUS; r += 3) {
            for (int a = 0; a < 8; a++) {
                double angle = a * Math.PI / 4;
                int x = feet.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = feet.getZ() + (int) Math.round(Math.sin(angle) * r);
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    static Arrival arrival(ServerLevel level, BlockPos feet, Plan plan) {
        return new Arrival(level, Vec3.atBottomCenterOf(feet), plan.yaw(), plan);
    }

    private static @Nullable BlockPos surface(ServerLevel level, int x, int z, boolean allowFluid) {
        if (UltraWarpRules.isUnderwater(level)) {
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (floor <= level.getMinBuildHeight() + 1) return null;
            BlockPos feet = new BlockPos(x, floor, z);
            return isHazard(level.getBlockState(feet.below())) ? null : feet;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1) return null;
        BlockPos feet = new BlockPos(x, y, z);
        BlockState below = level.getBlockState(feet.below());
        if (!below.getFluidState().isEmpty()) return allowFluid ? feet : null;
        return isHazard(below) ? null : feet;
    }

    static int caveTop(ServerLevel level) {
        return Math.min(NETHER_SEARCH_TOP, level.getMinBuildHeight() + level.getLogicalHeight() - 6);
    }

    private static @Nullable BlockPos caveFloor(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            BlockState below = level.getBlockState(pos.set(x, y - 1, z));
            if (!below.isFaceSturdy(level, pos, Direction.UP) || isHazard(below) || !below.getFluidState().isEmpty()) continue;
            if (level.getBlockState(pos.set(x, y, z)).isAir()
                    && level.getBlockState(pos.set(x, y + 1, z)).isAir()
                    && level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    static boolean isHazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.LAVA)
                || state.is(Blocks.BEDROCK) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    private static BlockPos naturalGroundNearCentre(ServerLevel level) {
        BlockPos centre = level.dimension() == Level.OVERWORLD ? level.getSharedSpawnPos() : BlockPos.ZERO;
        for (int r = 0; r <= 2048; r += 16) {
            int points = Math.max(1, r / 4);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                int x = centre.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = centre.getZ() + (int) Math.round(Math.sin(angle) * r);
                level.getChunk(x >> 4, z >> 4);
                BlockPos found = surface(level, x, z, false);
                if (found != null) return found;
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
    }

    static void spawnWild(Arrival arrival, boolean rainbow) {
        ServerLevel level = arrival.level();
        Plan plan = arrival.plan();
        String properties = plan.pokemon() != null ? plan.pokemon() : WormholePokemon.pick(plan.type(), plan.rings(), level.getRandom());
        if (properties == null) return;
        if (plan.rings() >= WarpTunnel.SHINY_RINGS) properties = properties.replace("shiny=false", "shiny=true");
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse(properties).createEntity(level);
            WormholePokemon.atLeastAverageSize(entity.getPokemon());
            if (level.getRandom().nextFloat() < WormholePokemon.alphaChance(plan.rings())) entity.getPokemon().setAlpha(true);
            entity.refreshDimensions();
            placeInOpenAir(entity, arrival);
            PokemonArrivals.start(level, entity, entity.position(), arrival.yaw() + 180f, plan.type(), true, rainbow);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't spawn the wormhole Pokémon '{}'", properties, e);
        }
    }

    private static void placeInOpenAir(PokemonEntity entity, Arrival arrival) {
        ServerLevel level = arrival.level();
        Plan plan = arrival.plan();
        float yaw = arrival.yaw() + 180f;
        int ay = Mth.floor(arrival.pos().y);
        int sideX = -plan.dirZ(), sideZ = plan.dirX();
        for (int ahead : PLACEHOLDER_AHEAD) {
            for (int side : PLACEHOLDER_SIDE) {
                int x = Mth.floor(arrival.pos().x) + plan.dirX() * ahead + sideX * side;
                int z = Mth.floor(arrival.pos().z) + plan.dirZ() * ahead + sideZ * side;
                BlockPos ground = level.dimensionType().hasCeiling()
                        ? caveFloor(level, x, z, ay + RETURN_MAX_RISE, ay - RETURN_MAX_RISE)
                        : surface(level, x, z, false);
                if (ground == null || Math.abs(ground.getY() - ay) > RETURN_MAX_RISE) continue;
                Vec3 pos = Vec3.atBottomCenterOf(ground);
                entity.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
                if (level.noCollision(entity) && (UltraWarpRules.isUnderwater(level) || !level.containsAnyLiquid(entity.getBoundingBox()))) return;
            }
        }
        Vec3 pos = arrival.pos().add(plan.dirX() * PLACEHOLDER_AHEAD[0], 0, plan.dirZ() * PLACEHOLDER_AHEAD[0]);
        Vec3 grounded = groundBelow(level, pos);
        if (grounded == pos || arrival.pos().y - grounded.y > RETURN_MAX_RISE) {
            pos = arrival.pos().add(-plan.dirZ() * 3, 0, plan.dirX() * 3);
            if (groundBelow(level, pos) == pos) pos = arrival.pos();
        } else {
            pos = grounded;
        }
        entity.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        for (int up = 1; up <= RETURN_MAX_CLIMB && !level.noCollision(entity); up++) {
            entity.moveTo(pos.x, pos.y + up, pos.z, yaw, 0f);
        }
    }

    private static int highestFloor(ServerLevel level, int x, int z, int reach, int least) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                least = Math.max(least, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz));
            }
        }
        return least;
    }

    static double floatHeight(ServerLevel level, Vec3 spot, int reach, double least) {
        if (level.dimensionType().hasCeiling()) return least;
        int highest = highestFloor(level, Mth.floor(spot.x), Mth.floor(spot.z), reach, Mth.floor(spot.y));
        return Mth.clamp(highest + FLOAT_CLEARANCE - spot.y, least, MAX_FLOAT);
    }

    static Vec3 floatStart(Arrival arrival, WormholeEntity exit) {
        Vec3 ground = arrival.pos();
        double wanted = exit.center().y - 1;
        double y = ground.y;
        while (y + 1 <= wanted) {
            AABB box = EntityType.PLAYER.getDimensions().makeBoundingBox(ground.x, y + 1, ground.z);
            if (!arrival.level().noCollision(box) || (!UltraWarpRules.isUnderwater(arrival.level()) && arrival.level().containsAnyLiquid(box))) break;
            y++;
        }
        return new Vec3(ground.x, y, ground.z);
    }

    private static boolean isOpen(ServerLevel level, BlockPos base) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RETURN_CLEAR_HALF_WIDTH; dx <= RETURN_CLEAR_HALF_WIDTH; dx++) {
            for (int dz = -RETURN_CLEAR_HALF_WIDTH; dz <= RETURN_CLEAR_HALF_WIDTH; dz++) {
                for (int dy = 0; dy < RETURN_CLEAR_HEIGHT; dy++) {
                    BlockState state = level.getBlockState(pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz));
                    if (!state.getCollisionShape(level, pos).isEmpty()) return false;
                    if (!state.getFluidState().isEmpty() && !UltraWarpRules.isUnderwater(level)) return false;
                }
            }
        }
        return true;
    }

    static Vec3 groundBelow(ServerLevel level, Vec3 pos) {
        BlockPos.MutableBlockPos feet = BlockPos.containing(pos).mutable();
        int top = level.getMaxBuildHeight() - 2;
        while (feet.getY() < top && !isPassable(level, feet)) feet.move(Direction.UP);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int y = feet.getY(); y > level.getMinBuildHeight(); y--) {
            BlockState state = level.getBlockState(below.set(feet.getX(), y - 1, feet.getZ()));
            if (!state.getCollisionShape(level, below).isEmpty()
                    || (!state.getFluidState().isEmpty() && !UltraWarpRules.isUnderwater(level))) {
                return new Vec3(pos.x, y, pos.z);
            }
        }
        return pos;
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    static WormholeEntity openReturnWormhole(Arrival arrival, HomePoint home, WormholeType type, int lifetimeTicks,
                                             ServerPlayer owner) {
        boolean ultraHome = UltraBeastWorlds.isHome(arrival.level());
        WormholeEntity wormhole = openBehind(arrival, type, ultraHome ? 0 : lifetimeTicks, false);
        wormhole.setReturnTo(home);
        if (ultraHome) {
            wormhole.setBeacon(true);
            WaysHome.trackUntilEmpty(wormhole, owner.getUUID());
        } else {
            WaysHome.track(wormhole, lifetimeTicks, owner.getUUID());
        }
        return wormhole;
    }

    static void openWayHomeBeside(ServerPlayer player, HomePoint home) {
        Arrival here = arrivalAt(player.serverLevel(), player.position(), player.getYRot());
        openReturnWormhole(here, home, WormholeType.WHITE, RETURN_WORMHOLE_TICKS, player);
    }

    static WormholeEntity openBehind(Arrival arrival, WormholeType type, int lifetimeTicks, boolean decorative) {
        ServerLevel level = arrival.level();
        Plan plan = arrival.plan();
        int ax = Mth.floor(arrival.pos().x), ay = Mth.floor(arrival.pos().y), az = Mth.floor(arrival.pos().z);
        int sideX = -plan.dirZ(), sideZ = plan.dirX();
        int x = ax - plan.dirX() * RETURN_BEHIND[0], z = az - plan.dirZ() * RETURN_BEHIND[0], groundY = ay;
        search:
        for (int back : RETURN_BEHIND) {
            for (int side : RETURN_SIDE) {
                int cx = ax - plan.dirX() * back + sideX * side;
                int cz = az - plan.dirZ() * back + sideZ * side;
                BlockPos ground = level.dimensionType().hasCeiling()
                        ? caveFloor(level, cx, cz, ay + RETURN_MAX_RISE, ay - RETURN_MAX_RISE)
                        : surface(level, cx, cz, false);
                if (ground == null || Math.abs(ground.getY() - ay) > RETURN_MAX_RISE) continue;
                x = cx;
                z = cz;
                groundY = ground.getY();
                break search;
            }
        }
        int top = openingHeight(level, x, z, groundY);
        BlockPos.MutableBlockPos base = new BlockPos.MutableBlockPos(x, top, z);
        int lowest = Math.min(groundY, ay) - RETURN_MAX_RISE;
        while (base.getY() - 1 >= lowest && isOpen(level, base.below())) base.move(Direction.DOWN);
        Vec3 start = Vec3.atBottomCenterOf(new BlockPos(x, top, z));
        Vec3 rest = Vec3.atBottomCenterOf(base);

        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setDecorative(decorative);
        wormhole.setWormholeType(type);
        wormhole.setLifetime(lifetimeTicks);
        Vec3 target = arrival.pos().add(0, EXIT_AIM_HEIGHT, 0);
        double across = Math.sqrt(Mth.square(target.x - rest.x) + Mth.square(target.z - rest.z));
        float yaw = across < 1 ? arrival.yaw() : (float) (Mth.atan2(-(target.x - rest.x), target.z - rest.z) * Mth.RAD_TO_DEG);
        wormhole.moveTo(start.x, start.y, start.z, yaw, RiderArrival.EXIT_PITCH);
        wormhole.sinkTo(rest.y);
        level.addFreshEntity(wormhole);
        WormholeSounds.open(level, start.add(0, WormholeEntity.SIZE / 2, 0), 0.6f, 1.7f);
        return wormhole;
    }

    private static int openingHeight(ServerLevel level, int x, int z, int groundY) {
        int wanted = level.dimensionType().hasCeiling() ? groundY + FLOAT_CLEARANCE + EXIT_LIFT
                : Mth.clamp(highestFloor(level, x, z, RETURN_CLEAR_HALF_WIDTH + 2, groundY) + FLOAT_CLEARANCE,
                        groundY + EXIT_LIFT, groundY + MAX_FLOAT);
        for (int y = wanted; y <= wanted + RETURN_MAX_CLIMB; y++) {
            if (isOpen(level, new BlockPos(x, y, z))) return y;
        }
        for (int y = wanted - 1; y > groundY; y--) {
            if (isOpen(level, new BlockPos(x, y, z))) return y;
        }
        return wanted;
    }
}

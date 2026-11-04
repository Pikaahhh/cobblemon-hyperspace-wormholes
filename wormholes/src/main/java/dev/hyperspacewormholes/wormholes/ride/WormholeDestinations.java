package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.WormholeConfig;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.world.Zone;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where tunnel wormholes take you: a Pokemon wormhole in the game's colours leads to its Ultra Space Wilds world, the
 * older difficulty colours to the Overworld, the End or now and then a Wilds world. It finds and clears a landing and
 * opens a way home (at the zone's exit hole where it has one), and in an Ultra Beast's home the beast stands at the
 * zone's spot and never moves. After NECROZMA_PITY white wormholes in a row the next white one is Necrozma's.
 */
public final class WormholeDestinations {
    private static final int MIN_DISTANCE = 64;
    private static final float EXIT_RADIUS = 3.82f;
    private static final double WILDS_CHANCE = 0.25;
    private static final int NECROZMA_PITY = 12;
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
    private static final int[] RETURN_SIDE = {0, 4, -4, 8, -8};
    private static final int RETURN_MAX_RISE = 6;
    private static final int RETURN_MAX_CLIMB = 24;
    private static final int RETURN_CLEAR_HALF_WIDTH = 2;
    private static final int RETURN_CLEAR_HEIGHT = 6;
    private static final int[] PLACEHOLDER_AHEAD = {9, 7, 11, 5, 13};
    private static final int[] PLACEHOLDER_SIDE = {0, 3, -3, 6, -6};
    private static final String WILDS_PREFIX = "ultra_wilds_";
    private static final double VISIT_LIGHT_YEARS = 5_000;
    public static final String STAYS_PUT = "hyperspace_wormholes.stays_put";

    private WormholeDestinations() {}

    public record Plan(ResourceKey<Level> dimension, int x, int z, int dirX, int dirZ, WormholeType type, int rings,
                       double lightYears, @Nullable String pokemon) {
        public float yaw() {
            return (float) (Mth.atan2(-dirX, dirZ) * Mth.RAD_TO_DEG);
        }
    }

    public record Arrival(ServerLevel level, Vec3 pos, float yaw, Plan plan) {}

    public static List<String> names() {
        List<String> names = new ArrayList<>(UltraBeastWorlds.HOMES.values().stream().map(w -> w.location().getPath()).sorted().toList());
        names.addAll(wildsNames());
        return names;
    }

    public static List<String> wildsNames() {
        return UltraZones.WILDS.stream().map(w -> w.location().getPath().substring(WILDS_PREFIX.length())).toList();
    }

    public static boolean isWilds(ResourceKey<Level> world) {
        return UltraZones.WILDS.contains(world);
    }

    public static @Nullable ResourceKey<Level> named(@Nullable String name) {
        if (name == null) return null;
        for (ResourceKey<Level> world : UltraBeastWorlds.HOMES.values()) {
            if (world.location().getPath().equals(name)) return world;
        }
        for (ResourceKey<Level> world : UltraZones.WILDS) {
            if (world.location().getPath().equals(WILDS_PREFIX + name)) return world;
        }
        return null;
    }

    public static WormholeType colourOf(ResourceKey<Level> world) {
        return isWilds(world) ? WormholeType.ofWilds(world) : WormholeType.WHITE;
    }

    public static void visit(ServerPlayer player, WormholeOrder order, HomePoint home) {
        ServerLevel level = player.server.getLevel(order.world());
        if (level == null) return;
        WormholeType type = colourOf(order.world());
        String pokemon = order.chosenPokemon(player.getRandom(), player);
        Zone zone = UltraZones.of(level);
        Arrival arrival;
        if (zone != null) {
            zone = zone.near(player.getX(), player.getZ());
            level.getChunk(zone.arrival());
            arrival = arrival(level, zone.arrival(), zonePlan(level, zone, type, order.rings(), VISIT_LIGHT_YEARS, pokemon));
        } else {
            keepLoaded(level, 0, 0);
            arrival = LandingClearing.clear(resolve(player.server,
                    new Plan(level.dimension(), 0, 0, 1, 0, type, order.rings(), VISIT_LIGHT_YEARS, pokemon)));
        }
        WormholeEntity exit = openReturnWormhole(arrival, home, type, RETURN_WORMHOLE_TICKS, player);
        UltraWarpRide.travel(player, arrival, exit);
        spawnWild(arrival, false, player);
    }

    static Plan orderPlan(ServerPlayer player, WormholeOrder order, @Nullable HomePoint start, double lightYears) {
        ServerLevel level = levelOr(player.server, order.world());
        WormholeType type = colourOf(order.world());
        String pokemon = order.chosenPokemon(player.getRandom(), player);
        Zone zone = UltraZones.of(level);
        if (zone != null) return zonePlan(level, near(zone, start), type, order.rings(), lightYears, pokemon);
        keepLoaded(level, 0, 0);
        return new Plan(level.dimension(), 0, 0, 1, 0, type, order.rings(), lightYears, pokemon);
    }

    static Arrival arrivalAt(ServerLevel level, Vec3 pos, float yaw) {
        int[] dir = axisOf(yaw);
        Plan plan = new Plan(level.dimension(), Mth.floor(pos.x), Mth.floor(pos.z), dir[0], dir[1], WormholeType.BLUE, 0, 0, null);
        return new Arrival(level, pos, yaw, plan);
    }

    private static int[] axisOf(float yaw) {
        return axis(-Mth.sin(yaw * Mth.DEG_TO_RAD), Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private static int[] axis(double fx, double fz) {
        int dirX = Math.abs(fx) > Math.abs(fz) ? (int) Math.signum(fx) : 0;
        return new int[]{dirX, dirX == 0 ? (fz < 0 ? -1 : 1) : 0};
    }

    private static Zone near(Zone zone, @Nullable HomePoint start) {
        return zone.near(start != null ? start.pos().x : 0, start != null ? start.pos().z : 0);
    }

    static ServerLevel levelOr(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        return level != null ? level : server.overworld();
    }

    static Plan plan(ServerPlayer player, WarpTunnel.Wormhole wormhole, long seed, double warpDistance,
                     @Nullable HomePoint start) {
        MinecraftServer server = player.server;
        double roll = new Random(seed ^ (wormhole.index() * 0xC2B2AE3D27D4EB4FL)).nextDouble();
        ResourceKey<Level> dimension = switch (wormhole.type()) {
            case WHITE -> Level.END;
            case GOLD, GREEN -> roll < 0.85 ? Level.OVERWORLD : Level.END;
            default -> Level.OVERWORLD;
        };
        Random wilds = new Random(seed ^ (wormhole.index() * 0x9E3779B97F4A7C15L) ^ 0x3A1D5L);
        boolean gameColours = WormholeConfig.get().gameWormholeColours;
        if (gameColours && wormhole.type().wildsWorld() != null) {
            dimension = wormhole.type().wildsWorld();
        } else if (!gameColours && (wormhole.type() == WormholeType.RED || wormhole.type() == WormholeType.ORANGE
                || wormhole.type() == WormholeType.GREEN) && wilds.nextDouble() < WILDS_CHANCE) {
            dimension = UltraZones.WILDS.get(wilds.nextInt(UltraZones.WILDS.size()));
        }
        String pokemon = null;
        if (wormhole.type() == WormholeType.WHITE) {
            RandomSource random = RandomSource.create(seed ^ (wormhole.index() * 0x5851F42D4C957F2DL));
            int missed = player.getAttachedOrElse(Wormholes.WHITE_WITHOUT_NECROZMA, 0);
            // no pity once this player has beaten Necrozma; its usual chance stays
            boolean beaten = player.getAttachedOrElse(Wormholes.NECROZMA_BEATEN, false);
            if (missed >= NECROZMA_PITY && !beaten) pokemon = WormholePokemon.ultraBeastNamed("necrozma", random);
            if (pokemon == null) {
                pokemon = gameColours ? WormholePokemon.ultraBeastWorld(wormhole.rings(), random)
                        : WormholePokemon.pick(WormholeType.WHITE, wormhole.rings(), random);
            }
            boolean necrozma = UltraBeastWorlds.homeOf(pokemon) == Wormholes.ULTRA_MEGALOPOLIS;
            player.setAttached(Wormholes.WHITE_WITHOUT_NECROZMA, necrozma ? 0 : missed + 1);
        }
        ResourceKey<Level> home = UltraBeastWorlds.homeOf(pokemon);
        if (home != null) dimension = home;
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            dimension = Level.OVERWORLD;
            level = server.overworld();
        }
        Zone zone = UltraZones.of(level);
        double lightYears = WarpTunnel.lightYears(warpDistance);
        if (zone != null) return zonePlan(level, near(zone, start), wormhole.type(), wormhole.rings(), lightYears, pokemon);
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
        return new Plan(dimension, x, z, dir[0], dir[1], wormhole.type(), wormhole.rings(), lightYears, pokemon);
    }

    static Plan planUncharted(MinecraftServer server, @Nullable HomePoint start, double warpDistance, long seed) {
        ServerLevel level = start == null || start.dimension() == Level.NETHER ? server.overworld() : levelOr(server, start.dimension());
        Vec3 from = start != null ? start.pos() : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        double angle = new Random(seed ^ 0x5EEDFA11L).nextDouble() * Math.PI * 2;
        double dx = -Math.sin(angle), dz = Math.cos(angle);
        double distance = Math.max(MIN_DISTANCE, warpDistance);
        double limit = level.getWorldBorder().getSize() / 2 - SEARCH_RANGE - 32;
        int x = (int) Mth.clamp(Math.round(from.x + dx * distance), -limit, limit);
        int z = (int) Mth.clamp(Math.round(from.z + dz * distance), -limit, limit);
        int[] dir = axis(dx, dz);
        keepLoaded(level, x, z);
        return new Plan(level.dimension(), x, z, dir[0], dir[1], WormholeType.GREEN, 0, 0, null);
    }

    static Plan zonePlan(ServerLevel level, Zone zone, WormholeType type, int rings, double lightYears,
                         @Nullable String pokemon) {
        int[] dir = axisOf(zone.yaw());
        keepLoaded(level, zone.arrival().getX(), zone.arrival().getZ());
        return new Plan(level.dimension(), zone.arrival().getX(), zone.arrival().getZ(), dir[0], dir[1], type, rings, lightYears,
                pokemon);
    }

    private static void keepLoaded(ServerLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, 64, z);
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
    }

    static Arrival resolve(MinecraftServer server, Plan plan) {
        ServerLevel level = levelOr(server, plan.dimension());
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

    static void spawnWild(Arrival arrival, boolean rainbow, @Nullable ServerPlayer player) {
        ServerLevel level = arrival.level();
        Plan plan = arrival.plan();
        Zone zone = UltraZones.of(level);
        if (zone != null) zone = zone.near(arrival.pos().x, arrival.pos().z);
        if (zone != null && level.dimension() == Wormholes.ULTRA_MEGALOPOLIS) {
            NecrozmaFight.arrive(level, zone, arrival.pos(), plan.pokemon());
            return;
        }
        boolean wilds = zone != null && UltraZones.WILDS.contains(level.dimension());
        String properties = plan.pokemon() != null ? plan.pokemon()
                : wilds ? WormholePokemon.wilds(level.dimension(), plan.type(), plan.rings(), plan.lightYears(),
                        level.getRandom(), player)
                : zone != null ? null
                : WormholePokemon.pick(plan.type(), plan.rings(), level.getRandom());
        if (properties == null) return;
        if (!WormholeConfig.get().gameWormholeColours && plan.rings() >= WarpTunnel.SHINY_RINGS) {
            properties = properties.replace("shiny=false", "shiny=true");
        }
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse(properties).createEntity(level);
            WormholePokemon.atLeastAverageSize(entity.getPokemon());
            boolean atSpot = zone != null && zone.spot() != null;
            boolean giant = atSpot && CraterGiants.fillsCrater(level, entity);
            if (!giant && level.getRandom().nextFloat() < WormholePokemon.alphaChance(plan.rings())) entity.getPokemon().setAlpha(true);
            if (giant) CraterGiants.fitCrater(entity);
            entity.refreshDimensions();
            entity.addTag(STAYS_PUT);
            UltraStays.keep(level, entity);
            if (wilds && atSpot) {
                WildsShadows.hide(level, entity, arrival.pos());
                return;
            }
            if (atSpot && ZoneLeaps.waits(level)) {
                ZoneLeaps.hide(level, entity, zone, arrival.pos());
                return;
            }
            if (atSpot) {
                Vec3 at = zone.standOn(zone.spot());
                if (giant) at = CraterGiants.onFloor(level, at);
                Vec3 faced = giant ? CraterGiants.walkEnd(zone, at) : null;
                if (faced == null) faced = arrival.pos();
                float facing = (float) (Mth.atan2(-(faced.x - at.x), faced.z - at.z) * Mth.RAD_TO_DEG);
                entity.moveTo(at.x, at.y, at.z, facing, 0f);
                entity.setYHeadRot(facing);
                entity.setYBodyRot(facing);
                entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), Mth.wrapDegrees(facing));
                entity.setNoAi(true);
                if (UltraPlantLightning.isUltraPlant(level)) PlantXurkitrees.sleep(entity);
                level.addFreshEntity(entity);
                return;
            }
            placeInOpenAir(entity, arrival);
            PokemonArrivals.start(level, entity, entity.position(), arrival.yaw() + 180f, plan.type(), rainbow);
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
                BlockPos ground = groundNear(level, x, z, ay);
                if (ground == null) continue;
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

    private static @Nullable BlockPos groundNear(ServerLevel level, int x, int z, int ay) {
        BlockPos ground = level.dimensionType().hasCeiling()
                ? caveFloor(level, x, z, ay + RETURN_MAX_RISE, ay - RETURN_MAX_RISE)
                : surface(level, x, z, false);
        return ground == null || Math.abs(ground.getY() - ay) > RETURN_MAX_RISE ? null : ground;
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
        Zone zone = UltraZones.of(level);
        if (zone != null) zone = zone.near(arrival.pos().x, arrival.pos().z);
        if (zone != null && zone.exit() != null) {
            Zone.Exit exit = zone.exit();
            float radius = Math.min(exit.radius(), EXIT_RADIUS);
            Vec3 centre = exit.centre();
            if (radius < exit.radius()) centre = new Vec3(centre.x, groundBelow(level, centre).y + radius, centre.z);
            Vec3 at = centre.subtract(0, WormholeEntity.SIZE / 2, 0);
            return open(level, at, exit.yaw(), exit.pitch(), radius / WormholeEntity.RIM, type, lifetimeTicks, decorative);
        }
        Plan plan = arrival.plan();
        int ax = Mth.floor(arrival.pos().x), ay = Mth.floor(arrival.pos().y), az = Mth.floor(arrival.pos().z);
        int sideX = -plan.dirZ(), sideZ = plan.dirX();
        int x = ax - plan.dirX() * RETURN_BEHIND[0], z = az - plan.dirZ() * RETURN_BEHIND[0], groundY = ay;
        search:
        for (int back : RETURN_BEHIND) {
            for (int side : RETURN_SIDE) {
                int cx = ax - plan.dirX() * back + sideX * side;
                int cz = az - plan.dirZ() * back + sideZ * side;
                BlockPos ground = groundNear(level, cx, cz, ay);
                if (ground == null) continue;
                x = cx;
                z = cz;
                groundY = ground.getY();
                break search;
            }
        }
        BlockPos.MutableBlockPos base = new BlockPos.MutableBlockPos(x, openingHeight(level, x, z, groundY), z);
        int lowest = Math.min(groundY, ay) - RETURN_MAX_RISE;
        while (base.getY() - 1 >= lowest && isOpen(level, base.below())) base.move(Direction.DOWN);
        Vec3 rest = Vec3.atBottomCenterOf(base);
        Vec3 target = arrival.pos().add(0, EXIT_AIM_HEIGHT, 0);
        double across = Math.sqrt(Mth.square(target.x - rest.x) + Mth.square(target.z - rest.z));
        float yaw = across < 1 ? arrival.yaw() : (float) (Mth.atan2(-(target.x - rest.x), target.z - rest.z) * Mth.RAD_TO_DEG);
        return open(level, rest, yaw, RiderArrival.EXIT_PITCH, 1f, type, lifetimeTicks, decorative);
    }

    static WormholeEntity open(ServerLevel level, Vec3 at, float yaw, float pitch, float scale,
                                       WormholeType type, int lifetimeTicks, boolean decorative) {
        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setDecorative(decorative);
        wormhole.setWormholeType(type);
        wormhole.setLifetime(lifetimeTicks);
        wormhole.setScale(scale);
        wormhole.moveTo(at.x, at.y, at.z, yaw, pitch);
        level.addFreshEntity(wormhole);
        WormholeSounds.open(level, at.add(0, WormholeEntity.SIZE / 2, 0), 0.6f, 1.7f);
        return wormhole;
    }

    private static int openingHeight(ServerLevel level, int x, int z, int groundY) {
        for (int y = groundY; y <= groundY + RETURN_MAX_CLIMB; y++) {
            if (isOpen(level, new BlockPos(x, y, z))) return y;
        }
        return groundY;
    }
}

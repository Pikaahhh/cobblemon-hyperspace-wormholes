package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hyperspace islands, copied from your own world. Copies the terrain around you into one of Hyperspace's island
 * slots with a blobby edge, works out where you arrive and where the way back is, and wipes leftover islands when
 * the server starts.
 */
public class Zones {
    public static final int BASE = 1000;
    public static final int SLOTS = 64;
    public static final int SPACING = 300;
    public static final int ROW_Z = 20000;

    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int REACH = 48;
    private static final int CLEAR = 55;
    private static final int DEEP = 20;
    private static final int OVERHANG = 3;
    private static final int PIT = 2;
    private static final int DENSE_REACH = 2;
    private static final int DENSE_DEPTH = 4;
    private static final double DENSE_SHARE = 0.75;
    private static final int COVER_REACH = 4;
    private static final int COVER_NEEDED = 20;
    private static final int CEILING = 3;
    private static final int PORTAL_TRIES = 16;
    private static final double PORTAL_INSET = 5;
    private static final double ARRIVAL_GAP = 3;
    private static final double UNEVEN_ARRIVAL = 50;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Set<String> SKIPPED_MODS = Set.of("chunkloaders", "rctmod", "waystones", "toms_storage",
            "storagedrawers", "travelersbackpack", "lootr");
    private static final Set<String> SKIPPED_BLOCKS = Set.of("minecraft:nether_portal", "minecraft:end_portal",
            "minecraft:end_gateway", "minecraft:fire", "minecraft:soul_fire", "minecraft:respawn_anchor",
            "cobblemon:healing_machine", "cobblemon:pc", "cobblemon:pc_top");

    private static Map<Integer, Zone> zones = new HashMap<>();
    private static int last = -1;

    public static class Zone {
        double ax, ay, az;
        float yaw;
        double rx, ry, rz, nx, nz;
        double cy;
        long made;
        List<String> types = new ArrayList<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putDouble("ax", ax);
            t.putDouble("ay", ay);
            t.putDouble("az", az);
            t.putFloat("yaw", yaw);
            t.putDouble("rx", rx);
            t.putDouble("ry", ry);
            t.putDouble("rz", rz);
            t.putDouble("nx", nx);
            t.putDouble("nz", nz);
            t.putDouble("cy", cy);
            t.putLong("made", made);
            ListTag list = new ListTag();
            for (String type : types) list.add(StringTag.valueOf(type));
            t.put("types", list);
            return t;
        }

        static Zone load(CompoundTag t) {
            Zone zone = new Zone();
            zone.ax = t.getDouble("ax");
            zone.ay = t.getDouble("ay");
            zone.az = t.getDouble("az");
            zone.yaw = t.getFloat("yaw");
            zone.rx = t.getDouble("rx");
            zone.ry = t.getDouble("ry");
            zone.rz = t.getDouble("rz");
            zone.nx = t.getDouble("nx");
            zone.nz = t.getDouble("nz");
            zone.cy = t.contains("cy") ? t.getDouble("cy") : zone.ay;
            zone.made = t.getLong("made");
            ListTag list = t.getList("types", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) zone.types.add(list.getString(i));
            return zone;
        }
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(Zones::wipe);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WorldData data = WorldData.get(server);
            data.zones.clear();
            data.tasks.keySet().removeIf(island -> island >= BASE);
            data.setDirty();
            zones = data.zones;
        });
    }

    private static void wipe(MinecraftServer server) {
        ResourceLocation id = Hyperspace.HYPERSPACE.location();
        Path dimension = server.getWorldPath(LevelResource.ROOT).resolve("dimensions")
                .resolve(id.getNamespace()).resolve(id.getPath());
        int from = Math.floorDiv(ROW_Z - SPACING / 2, 512);
        int to = Math.floorDiv(ROW_Z + SPACING / 2, 512);
        for (String folder : List.of("region", "entities", "poi")) {
            try (Stream<Path> files = Files.list(dimension.resolve(folder))) {
                for (Path file : files.toList()) {
                    String[] parts = file.getFileName().toString().split("\\.");
                    if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca")) continue;
                    int z = Integer.parseInt(parts[2]);
                    if (z >= from && z <= to) Files.delete(file);
                }
            } catch (NoSuchFileException ignored) {
            } catch (IOException | NumberFormatException e) {
                Hyperspace.LOGGER.error("Couldn't wipe the Hyperspace zones in {}", folder, e);
            }
        }
    }

    public static boolean isZone(int island) {
        return island >= BASE && zones.containsKey(island - BASE);
    }

    public static Zone get(int island) {
        return zones.get(island - BASE);
    }

    public static int islandAt(BlockPos pos) {
        int slot = Math.floorDiv(pos.getX() + SPACING / 2, SPACING);
        if (slot < 0 || slot >= SLOTS || Math.abs(pos.getZ() - ROW_Z) > SPACING / 2) return -1;
        return BASE + slot;
    }

    public static boolean leftover(BlockPos pos, long born) {
        int island = islandAt(pos);
        if (island < 0) return false;
        Zone zone = get(island);
        return zone == null || born < zone.made;
    }

    public static BlockPos centre(int slot) {
        return new BlockPos(slot * SPACING, 64, ROW_Z);
    }

    public static BlockPos middle(int island) {
        BlockPos centre = centre(island - BASE);
        return new BlockPos(centre.getX(), (int) Math.round(get(island).cy), centre.getZ());
    }

    private static int stand(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        while (pos.getY() > level.getMinBuildHeight()) {
            pos.move(0, -1, 0);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getCollisionShape(level, pos).isEmpty()) continue;
            return pos.getY() + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static void edgePortal(ServerLevel level, BlockPos centre, int slot, Zone zone) {
        int mid = stand(level, centre.getX(), centre.getZ());
        zone.cy = mid == Integer.MIN_VALUE ? zone.ay : mid;
        double best = Double.MAX_VALUE, angle = 0;
        for (int k = 0; k < PORTAL_TRIES; k++) {
            double a = (k + slot * 0.37) * Math.PI * 2 / PORTAL_TRIES;
            double r = edge(slot, a) - PORTAL_INSET;
            double px = centre.getX() + Math.round(Math.cos(a) * r) + 0.5, pz = centre.getZ() + Math.round(Math.sin(a) * r) + 0.5;
            int y = stand(level, (int) Math.floor(px), (int) Math.floor(pz));
            if (y == Integer.MIN_VALUE) continue;
            int ay = stand(level, (int) Math.floor(px - Math.cos(a) * ARRIVAL_GAP), (int) Math.floor(pz - Math.sin(a) * ARRIVAL_GAP));
            double score = Math.abs(y - zone.cy) + (ay == y ? 0 : UNEVEN_ARRIVAL);
            if (score >= best) continue;
            best = score;
            angle = a;
            zone.rx = px;
            zone.ry = y;
            zone.rz = pz;
        }
        if (best == Double.MAX_VALUE) return;
        zone.nx = -Math.cos(angle);
        zone.nz = -Math.sin(angle);
        clearAbove(level, BlockPos.containing(zone.rx, zone.ry, zone.rz));
        zone.ax = zone.rx + zone.nx * ARRIVAL_GAP;
        zone.az = zone.rz + zone.nz * ARRIVAL_GAP;
        zone.ay = zone.ry;
        clearAbove(level, BlockPos.containing(zone.ax, zone.ay, zone.az));
        zone.yaw = (float) Math.toDegrees(Math.atan2(-zone.nx, zone.nz));
    }

    public static void release(ServerLevel level, int island) {
        int slot = island - BASE;
        if (!zones.containsKey(slot)) return;
        BlockPos centre = centre(slot);
        AABB area = new AABB(centre).inflate(CLEAR, level.getHeight(), CLEAR);
        for (Entity entity : level.getEntities((Entity) null, area, entity -> !(entity instanceof Player))) entity.discard();
        clear(level, centre);
        zones.remove(slot);
        WorldData.get(level.getServer()).setDirty();
    }

    private static void clearAbove(ServerLevel level, BlockPos base) {
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 3, 1))) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, QUIET);
        }
    }

    public static double edge(int slot, double angle) {
        return 34 + 4.4 * Math.sin(2 * angle + slot * 1.7) + 3.1 * Math.sin(3 * angle + slot * 0.9)
                + 1.9 * Math.sin(5 * angle + slot * 2.3);
    }

    /** The island's blocks swapped for the Hoopa arena where it stands, with the way home on it (/hyperspace arena). */
    public static void arena(ServerLevel hyperspace, int island) {
        BlockPos centre = centre(island - BASE);
        clear(hyperspace, centre);
        HoopaArena.build(hyperspace, centre, island - BASE, get(island));
    }

    public static int build(ServerPlayer player, ServerLevel hyperspace, ServerLevel from, Vec3 portalBase, Vec3 normal, Set<Integer> busy, boolean arena) {
        int slot = -1;
        for (int k = 1; k <= SLOTS; k++) {
            int candidate = (last + k) % SLOTS;
            if (!busy.contains(BASE + candidate)) {
                slot = candidate;
                break;
            }
        }
        if (slot < 0) return -1;
        last = slot;

        BlockPos origin = BlockPos.containing(portalBase);
        BlockPos centre = centre(slot);
        clear(hyperspace, centre);
        Zone zone = new Zone();
        zone.made = hyperspace.getGameTime();
        if (arena) {
            HoopaArena.build(hyperspace, centre, slot, zone);
            zone.types = types(from.getBiome(origin));
            zones.put(slot, zone);
            WorldData.get(player.server).setDirty();
            return BASE + slot;
        }
        tidy(hyperspace, copy(hyperspace, from, origin, centre, slot));

        zone.ax = centre.getX() + (player.getX() - origin.getX());
        zone.ay = player.getY();
        zone.az = centre.getZ() + (player.getZ() - origin.getZ());
        zone.yaw = player.getYRot();
        zone.rx = centre.getX() + (portalBase.x - origin.getX());
        zone.ry = portalBase.y;
        zone.rz = centre.getZ() + (portalBase.z - origin.getZ());
        zone.nx = normal.x;
        zone.nz = normal.z;
        edgePortal(hyperspace, centre, slot, zone);
        IslandWall.place(hyperspace, centre, slot, zone);
        FloatingBalls.scatter(hyperspace, centre, slot, zone);
        zone.types = types(from.getBiome(origin));
        zones.put(slot, zone);
        WorldData.get(player.server).setDirty();
        return BASE + slot;
    }

    private static void clear(ServerLevel hyperspace, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centre.getX() - CLEAR; x <= centre.getX() + CLEAR; x++) {
            for (int z = centre.getZ() - CLEAR; z <= centre.getZ() + CLEAR; z++) {
                ChunkAccess chunk = hyperspace.getChunk(x >> 4, z >> 4);
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                for (int y = hyperspace.getMinBuildHeight(); y <= top; y++) {
                    if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) hyperspace.setBlock(pos, AIR, QUIET);
                }
            }
        }
    }

    private static BlockState dry(BlockState state) {
        if (!state.getFluidState().is(FluidTags.WATER)) return state;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) return state.setValue(BlockStateProperties.WATERLOGGED, false);
        return AIR;
    }

    private static boolean skipped(BlockState state, ServerLevel from, BlockPos pos) {
        if (skippedHere(state, from, pos)) return true;
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            BlockState other = from.getBlockState(side);
            if (other.is(state.getBlock()) && skippedHere(other, from, side)) return true;
        }
        return false;
    }

    private static boolean skippedHere(BlockState state, ServerLevel from, BlockPos pos) {
        if (state.hasBlockEntity() || state.getMenuProvider(from, pos) != null) return true;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return SKIPPED_BLOCKS.contains(id) || SKIPPED_MODS.contains(id.substring(0, id.indexOf(':')));
    }

    private static BlockState underground(BlockState state, BlockState rock) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return rock;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.contains("ore") || id.startsWith("raw_") || id.contains("amethyst") || id.contains("dripstone")
                || id.contains("lichen") || id.contains("cobweb")) return rock;
        return state;
    }

    private static boolean covered(ServerLevel from, BlockPos origin) {
        if (from.dimensionType().hasCeiling()) return true;
        int roofed = 0;
        for (int dx = -COVER_REACH; dx <= COVER_REACH; dx += COVER_REACH / 2) {
            for (int dz = -COVER_REACH; dz <= COVER_REACH; dz += COVER_REACH / 2) {
                if (rockAbove(from, origin.getX() + dx, origin.getY() + 3, origin.getZ() + dz)) roofed++;
            }
        }
        return roofed >= COVER_NEEDED;
    }

    private static boolean rockAbove(ServerLevel from, int x, int y, int z) {
        int top = from.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        int run = 0;
        for (; y < top; y++) {
            run = natural(from.getBlockState(pos.setY(y))) ? run + 1 : 0;
            if (run >= CEILING) return true;
        }
        return false;
    }

    private static boolean solid(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty();
    }

    private static int[] column(ChunkAccess chunk, int x, int z, int y, int lowest) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        int ground = y;
        if (solid(chunk, pos)) {
            while (ground < y + 6 && solid(chunk, pos.setY(ground + 1))) ground++;
            if (ground == y + 6) return new int[]{ground, ground};
        } else {
            while (ground > lowest && !solid(chunk, pos.setY(ground))) ground--;
        }
        int top = ground;
        while (top < ground + 32 && !chunk.getBlockState(pos.setY(top + 1)).isAir()) top++;
        while (top < ground + 40 && chunk.getBlockState(pos.setY(top + 1)).isAir()) top++;
        return new int[]{ground, top};
    }

    private static BlockState[] palette(ServerLevel from) {
        BlockState rock = from.dimension() == Level.NETHER ? Blocks.NETHERRACK.defaultBlockState()
                : from.dimension() == Level.END ? Blocks.END_STONE.defaultBlockState() : null;
        if (rock != null) return new BlockState[]{rock, rock, rock};
        return new BlockState[]{Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState()};
    }

    private static boolean natural(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || state.is(BlockTags.NETHER_CARVER_REPLACEABLES)) return true;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.contains("ore") || id.equals("end_stone");
    }

    private static int groundAt(ServerLevel from, ChunkAccess chunk, int x, int z, int top, int lowest) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > lowest; y--) {
            if (natural(chunk.getBlockState(pos.setY(y))) && dense(from, x, y, z)) return y;
        }
        return lowest;
    }

    private static boolean dense(ServerLevel from, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int filled = 0, total = 0;
        for (int dy = 1; dy <= DENSE_DEPTH; dy++) {
            for (int dx = -DENSE_REACH; dx <= DENSE_REACH; dx++) {
                for (int dz = -DENSE_REACH; dz <= DENSE_REACH; dz++) {
                    total++;
                    if (solid(from, pos.set(x + dx, y - dy, z + dz))) filled++;
                }
            }
        }
        return filled >= total * DENSE_SHARE;
    }

    private static List<BlockPos> copy(ServerLevel hyperspace, ServerLevel from, BlockPos origin, BlockPos centre, int slot) {
        int lowest = from.getMinBuildHeight(), highest = from.getMaxBuildHeight() - 1;
        boolean covered = covered(from, origin);
        BlockState[] palette = palette(from);
        int size = REACH * 2 + 1;
        int[][] grounds = new int[size][size], wets = new int[size][size], tops = new int[size][size], fills = new int[size][size];
        boolean[][] used = new boolean[size][size], inside = new boolean[size][size];
        List<BlockPos> placed = new ArrayList<>();
        BlockPos.MutableBlockPos read = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double r = edge(slot, Math.atan2(dz, dx));
                if (d > r + OVERHANG) continue;
                int sx = origin.getX() + dx, sz = origin.getZ() + dz;
                ChunkAccess chunk = from.getChunk(sx >> 4, sz >> 4);
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sx & 15, sz & 15);
                int[] heights = covered ? column(chunk, sx, sz, origin.getY(), lowest)
                        : new int[]{groundAt(from, chunk, sx, sz, Math.min(surface, highest), lowest), surface};
                int ground = heights[0], top = heights[1];
                if (ground <= lowest) continue;
                int i = dx + REACH, j = dz + REACH;
                used[i][j] = true;
                inside[i][j] = d <= r;
                int wet = ground;
                while (wet < top && !chunk.getBlockState(read.set(sx, wet + 1, sz)).getFluidState().isEmpty()) wet++;
                grounds[i][j] = ground;
                wets[i][j] = wet;
                tops[i][j] = top;
            }
        }

        int[][] raised = new int[size][size];
        for (int pass = 0; pass < 2; pass++) {
            int[][] in = pass == 0 ? wets : raised, out = pass == 0 ? raised : fills;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (!inside[i][j]) continue;
                    int m = in[i][j];
                    for (int a = Math.max(0, i - PIT); a <= Math.min(size - 1, i + PIT); a++) {
                        for (int b = Math.max(0, j - PIT); b <= Math.min(size - 1, j + PIT); b++) {
                            if (inside[a][b]) m = pass == 0 ? Math.max(m, in[a][b]) : Math.min(m, in[a][b]);
                        }
                    }
                    out[i][j] = m;
                }
            }
        }

        Map<Long, Integer> bottoms = new HashMap<>();
        List<BlockPos> fluids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!used[i][j]) continue;
                int dx = i - REACH, dz = j - REACH;
                double d = Math.sqrt(dx * dx + dz * dz);
                double r = edge(slot, Math.atan2(dz, dx));
                int sx = origin.getX() + dx, sz = origin.getZ() + dz;
                ChunkAccess chunk = from.getChunk(sx >> 4, sz >> 4);
                int ground = grounds[i][j];
                int fill = inside[i][j] && fills[i][j] > wets[i][j] && open(chunk, read, sx, sz, ground, fills[i][j]) ? fills[i][j] : ground;
                int floor = Math.min(ground, origin.getY());
                int bottom = inside[i][j] ? floor - 4 - (int) Math.round(DEEP * Math.sqrt(Math.max(0, 1 - (d / r) * (d / r)))) : ground + 1;
                int tx = centre.getX() + dx, tz = centre.getZ() + dz;
                bottoms.put(BlockPos.asLong(tx, 0, tz), bottom);
                // a filled dip wears its own ground and snow, not plain grass
                BlockState own = chunk.getBlockState(read.set(sx, ground, sz)), lying = chunk.getBlockState(read.set(sx, ground + 1, sz));
                for (int y = Math.max(bottom, lowest); y <= Math.min(Math.max(tops[i][j], fill), highest); y++) {
                    BlockState state = chunk.getBlockState(read.set(sx, y, sz));
                    if (!inside[i][j] && !state.is(BlockTags.LEAVES)) continue;
                    if (y < ground) {
                        state = underground(state, palette[2]);
                    } else if (y > ground && y <= fill && (state.isAir() || !state.getFluidState().isEmpty() || state.getCollisionShape(chunk, read).isEmpty())) {
                        state = y == fill ? own : y == fill - 1 ? palette[1] : palette[2];
                    }
                    state = dry(state);
                    if (state.isAir() || skipped(state, from, read)) continue;
                    BlockPos to = new BlockPos(tx, y, tz);
                    hyperspace.setBlock(to, state, QUIET);
                    placed.add(to);
                    if (!state.getFluidState().isEmpty()) fluids.add(to);
                }
                BlockPos over = new BlockPos(tx, fill + 1, tz);
                if (inside[i][j] && fill > ground && lying.is(Blocks.SNOW) && hyperspace.getBlockState(over).isAir()) {
                    hyperspace.setBlock(over, lying, QUIET);
                    placed.add(over);
                }
            }
        }
        for (BlockPos pos : fluids) {
            for (BlockPos side : new BlockPos[]{pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
                if (!hyperspace.getBlockState(side).isAir()) continue;
                int dx = side.getX() - centre.getX(), dz = side.getZ() - centre.getZ();
                boolean outside = Math.sqrt(dx * dx + dz * dz) > edge(slot, Math.atan2(dz, dx));
                Integer bottom = bottoms.get(BlockPos.asLong(side.getX(), 0, side.getZ()));
                if (outside || bottom == null || side.getY() < bottom || side.getY() < pos.getY()) hyperspace.setBlock(side, palette[2], QUIET);
            }
        }
        return placed;
    }

    private static boolean open(ChunkAccess chunk, BlockPos.MutableBlockPos read, int x, int z, int ground, int fill) {
        for (int y = ground + 1; y <= fill; y++) {
            BlockState state = chunk.getBlockState(read.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) return false;
        }
        return true;
    }

    private static void tidy(ServerLevel level, List<BlockPos> placed) {
        Map<BlockPos, Integer> reach = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> leaves = new ArrayList<>();
        for (BlockPos pos : placed) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) leaves.add(pos);
            else if (state.is(BlockTags.LOGS)) {
                reach.put(pos, 0);
                queue.add(pos);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int d = reach.get(pos);
            if (d >= 6) continue;
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (reach.containsKey(next) || !level.getBlockState(next).is(BlockTags.LEAVES)) continue;
                reach.put(next, d + 1);
                queue.add(next);
            }
        }
        ArrayDeque<BlockPos> check = new ArrayDeque<>(placed);
        for (BlockPos pos : leaves) {
            if (reach.containsKey(pos)) continue;
            level.setBlock(pos, AIR, QUIET);
            for (Direction dir : Direction.values()) check.add(pos.relative(dir));
        }
        int limit = placed.size() * 3 + 10000;
        while (!check.isEmpty() && limit-- > 0) {
            BlockPos pos = check.poll();
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock) continue;
            boolean falls = state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below()));
            if (!falls && state.canSurvive(level, pos)) continue;
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), QUIET);
            for (Direction dir : Direction.values()) check.add(pos.relative(dir));
        }
    }

    static List<String> types(Holder<Biome> biome) {
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (biome.is(BiomeTags.IS_NETHER)) return List.of("fire", "dark");
        if (biome.is(BiomeTags.IS_END)) return List.of("dragon", "psychic");
        if (path.contains("deep_dark")) return List.of("ghost", "dark");
        if (path.contains("swamp") || path.contains("bog") || path.contains("marsh")) return List.of("poison", "water");
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return List.of("water");
        if (biome.is(BiomeTags.IS_RIVER)) return List.of("water", "normal");
        if (biome.is(BiomeTags.IS_BEACH)) return List.of("water", "ground");
        if (path.contains("mushroom")) return List.of("psychic", "fairy");
        if (path.contains("cherry") || path.contains("flower") || path.contains("meadow") || path.contains("lavender")) return List.of("fairy", "grass");
        if (path.contains("dark_forest") || path.contains("dead") || path.contains("spooky")) return List.of("ghost", "dark");
        if (biome.is(BiomeTags.IS_JUNGLE)) return List.of("bug", "grass");
        if (path.contains("desert") || path.contains("dune")) return List.of("ground", "fire");
        if (biome.is(BiomeTags.IS_BADLANDS)) return List.of("ground", "rock");
        if (path.contains("ice") || path.contains("frozen") || path.contains("snow") || path.contains("tundra")) return List.of("ice");
        if (path.contains("peak") || path.contains("slope") || path.contains("windswept") || path.contains("stony") || path.contains("crag")) return List.of("rock", "flying");
        if (biome.is(BiomeTags.IS_SAVANNA)) return List.of("normal", "fire");
        if (path.contains("cave")) return List.of("rock", "steel");
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) return List.of("grass", "bug");
        return List.of("normal", "grass");
    }
}

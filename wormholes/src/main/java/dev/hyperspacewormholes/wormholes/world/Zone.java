package dev.hyperspacewormholes.wormholes.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * One of the small playable Ultra Space areas from Ultra Sun and Ultra Moon, voxelised from the game's models at player
 * scale and closed in so the void is never in view. Blocks are vanilla structure templates (TemplateTiles, folder zones)
 * with air not stored; NAME.dat in data/hyperspace_wormholes/zones holds the rest (grid corner and size, arrival, exit hole,
 * ceiling, walk mask, tile list) and an optional NAME.ground holds the terrain heights around it. Zones repeat every REPEAT
 * blocks on both axes except the four Wilds, and near() gives the copy nearest a position.
 */
public final class Zone {
    private static final Map<String, Zone> LOADED = new ConcurrentHashMap<>();
    private static final String FOLDER = "zones";
    private static final int REPEAT = 10_000;
    private static final String UNREPEATED = "wilds_";

    private record Ground(int x0, int z0, int sx, int sz, short[] height, byte[] weight) {}

    private final int x0, y0, z0, sx, sz, ceiling, repeat;
    private final @Nullable Ground ground;
    private final BlockPos arrival;
    private final @Nullable BlockPos spot;
    private final @Nullable Exit exit;
    private final float yaw;
    private final BlockState[] palette;
    private final byte[] walk;
    private final int[] start;
    private final short[] runs;
    private final Map<Integer, float[]> around = new ConcurrentHashMap<>();

    public record Exit(Vec3 centre, float yaw, float pitch, float radius) {}

    private Zone(int x0, int y0, int z0, int sx, int sz, int ceiling, int repeat, BlockPos arrival,
                 float yaw, @Nullable BlockPos spot, @Nullable Exit exit, BlockState[] palette, byte[] walk, int[] start,
                 short[] runs, @Nullable Ground ground) {
        this.ground = ground;
        this.repeat = repeat;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.sx = sx;
        this.sz = sz;
        this.ceiling = ceiling;
        this.arrival = arrival;
        this.spot = spot;
        this.exit = exit;
        this.yaw = yaw;
        this.palette = palette;
        this.walk = walk;
        this.start = start;
        this.runs = runs;
    }

    public static Zone get(String name) {
        Zone zone = LOADED.get(name);
        return zone != null ? zone : LOADED.computeIfAbsent(name, Zone::load);
    }

    private static Zone load(String name) {
        try {
            CompoundTag data = TemplateTiles.data(FOLDER, name);
            TemplateTiles.Columns columns = TemplateTiles.columns(FOLDER, name, data);
            int[] corner = data.getIntArray("corner"), size = data.getIntArray("size"), at = data.getIntArray("arrival");
            BlockPos arrival = new BlockPos(at[0], at[1], at[2]);
            int[] held = data.getIntArray("spot");
            BlockPos spot = held.length == 0 ? null : new BlockPos(held[0], held[1], held[2]);
            float[] hole = floats(data.getIntArray("exit"));
            Exit exit = hole.length == 0 ? null : new Exit(new Vec3(hole[0], hole[1], hole[2]), hole[3], hole[4], hole[5]);
            int ceiling = data.getInt("ceiling");
            BlockState[] palette = new BlockState[columns.palette().length];
            for (int i = 0; i < palette.length; i++) palette[i] = parse(columns.palette()[i]);
            Zone zone = new Zone(corner[0], corner[1], corner[2], size[0], size[2], ceiling, name.startsWith(UNREPEATED) ? 0 : REPEAT,
                    arrival, data.getFloat("yaw"), spot, exit, palette, data.getByteArray("walk"), columns.start(), columns.runs(),
                    readGround(name));
            return zone;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // a float tag can't hold the exit's -0 pitch, so the floats are stored as raw bits
    private static float[] floats(int[] bits) {
        float[] values = new float[bits.length];
        for (int i = 0; i < bits.length; i++) values[i] = Float.intBitsToFloat(bits[i]);
        return values;
    }

    private static @Nullable Ground readGround(String name) throws IOException {
        String path = "/data/hyperspace_wormholes/zones/" + name + ".ground";
        try (InputStream raw = Zone.class.getResourceAsStream(path)) {
            if (raw == null) return null;
            DataInputStream in = open(raw);
            if (!magic(in).equals("HWG1")) throw new IOException("not a ground file: " + path);
            int x0 = in.readInt(), z0 = in.readInt(), sx = in.readInt(), sz = in.readInt();
            short[] height = new short[sx * sz];
            byte[] weight = new byte[sx * sz];
            for (int i = 0; i < sx * sz; i++) {
                height[i] = in.readShort();
                weight[i] = in.readByte();
            }
            return new Ground(x0, z0, sx, sz, height, weight);
        }
    }

    private static DataInputStream open(InputStream raw) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
    }

    private static String magic(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        return new String(magic);
    }

    public static BlockState block(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(location) ? BuiltInRegistries.BLOCK.get(location).defaultBlockState()
                : Blocks.AIR.defaultBlockState();
    }

    private static BlockState parse(String state) throws IOException {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IOException("bad block state " + state, e);
        }
    }

    public Zone near(double x, double z) {
        int dx = copy(x, x0, sx), dz = copy(z, z0, sz);
        if (dx == 0 && dz == 0) return this;
        Exit moved = exit == null ? null : new Exit(exit.centre().add(dx, 0, dz), exit.yaw(), exit.pitch(), exit.radius());
        return new Zone(x0 + dx, y0, z0 + dz, sx, sz, ceiling, repeat, arrival.offset(dx, 0, dz), yaw,
                spot == null ? null : spot.offset(dx, 0, dz), moved, palette, walk, start, runs, ground);
    }
    public double fromCentre(int x, int z) {
        return Math.hypot(x - x0 - copy(x, x0, sx) - sx / 2, z - z0 - copy(z, z0, sz) - sz / 2);
    }
    public boolean nearHoldsAtOrBelow(int x, int y, int z) {
        return holdsAtOrBelow(x - copy(x, x0, sx), y, z - copy(z, z0, sz));
    }

    public double ground(int x, int z, double world) {
        int i = groundIndex(x, z);
        if (i < 0) return world;
        return world + (ground.height()[i] / 16.0 - world) * ((ground.weight()[i] & 0xFF) / 255.0);
    }

    public double surrounds(int x, int z) {
        int i = groundIndex(x, z);
        return i < 0 ? 0 : (ground.weight()[i] & 0xFF) / 255.0;
    }

    private int groundIndex(int x, int z) {
        if (ground == null) return -1;
        int gx = x - copy(x, x0, sx) - ground.x0(), gz = z - copy(z, z0, sz) - ground.z0();
        if (gx < 0 || gz < 0 || gx >= ground.sx() || gz >= ground.sz()) return -1;
        return gx * ground.sz() + gz;
    }

    public boolean overlaps(int x, int z, int reach) {
        return x + reach >= x0 && x - reach < x0 + sx && z + reach >= z0 && z - reach < z0 + sz;
    }

    public boolean clearOf(Zone other, int ax, int az, Rotation rotation) {
        for (int gx = 0; gx < sx; gx++) {
            for (int gz = 0; gz < sz; gz++) {
                if (!filled(gx, gz)) continue;
                BlockPos to = new BlockPos(x0 + gx - arrival.getX(), 0, z0 + gz - arrival.getZ()).rotate(rotation);
                if (other.holdsAtOrBelow(ax + to.getX(), Integer.MAX_VALUE, az + to.getZ())) return false;
            }
        }
        return true;
    }

    public float distanceTo(int ax, int az, Rotation rotation, int x, int z, int pad) {
        BlockPos from = new BlockPos(x - ax, 0, z - az).rotate(inverse(rotation));
        int gx = arrival.getX() + from.getX() - x0 + pad, gz = arrival.getZ() + from.getZ() - z0 + pad;
        int wx = sx + 2 * pad, wz = sz + 2 * pad;
        if (gx < 0 || gz < 0 || gx >= wx || gz >= wz) return Float.MAX_VALUE;
        return around.computeIfAbsent(pad, this::around)[gx * wz + gz];
    }

    private float[] around(int pad) {
        List<int[]> edge = new ArrayList<>();
        for (int gx = 0; gx < sx; gx++) {
            for (int gz = 0; gz < sz; gz++) {
                if (filled(gx, gz) && (!filled(gx + 1, gz) || !filled(gx - 1, gz) || !filled(gx, gz + 1) || !filled(gx, gz - 1))) {
                    edge.add(new int[]{gx, gz});
                }
            }
        }
        int wx = sx + 2 * pad, wz = sz + 2 * pad;
        float[] distance = new float[wx * wz];
        for (int px = 0; px < wx; px++) {
            for (int pz = 0; pz < wz; pz++) {
                int gx = px - pad, gz = pz - pad;
                float best = filled(gx, gz) ? 0 : Float.MAX_VALUE;
                for (int i = 0; best > 0 && i < edge.size(); i++) {
                    int dx = edge.get(i)[0] - gx, dz = edge.get(i)[1] - gz;
                    best = Math.min(best, (float) Math.sqrt(dx * dx + dz * dz));
                }
                distance[px * wz + pz] = best;
            }
        }
        return distance;
    }

    private boolean filled(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= sx || gz >= sz) return false;
        int column = gx * sz + gz;
        return start[column] < start[column + 1];
    }

    private static Rotation inverse(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 ? Rotation.COUNTERCLOCKWISE_90
                : rotation == Rotation.COUNTERCLOCKWISE_90 ? Rotation.CLOCKWISE_90 : rotation;
    }

    public int reach() {
        int dx = Math.max(arrival.getX() - x0, x0 + sx - 1 - arrival.getX());
        int dz = Math.max(arrival.getZ() - z0, z0 + sz - 1 - arrival.getZ());
        return (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
    }

    public BlockPos centre() {
        return new BlockPos(x0 + sx / 2, y0, z0 + sz / 2);
    }

    private int copy(double v, int origin, int size) {
        return repeat == 0 ? 0 : (int) Math.round((v - origin - size / 2.0) / repeat) * repeat;
    }

    public void placeTurned(WorldGenLevel level, ChunkPos chunk, int ax, int ay, int az, Rotation rotation) {
        placeTurned(level, chunk, ax, ay, az, rotation, false);
    }

    public void placeTurned(WorldGenLevel level, ChunkPos chunk, int ax, int ay, int az, Rotation rotation, boolean shade) {
        Rotation back = inverse(rotation);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                BlockPos from = new BlockPos(x - ax, 0, z - az).rotate(back);
                int gx = arrival.getX() + from.getX() - x0, gz = arrival.getZ() + from.getZ() - z0;
                if (gx < 0 || gx >= sx || gz < 0 || gz >= sz) continue;
                int column = gx * sz + gz;
                for (int r = start[column]; r < start[column + 1]; r += 3) {
                    BlockState state = palette[runs[r + 2]].rotate(rotation);
                    int bottom = ay + y0 + runs[r] - arrival.getY();
                    for (int y = bottom; y < bottom + runs[r + 1]; y++) {
                        BlockState placed = shade ? Blends.pick(state, x, y, z,
                                (y - ay + arrival.getY() - y0) / (double) (ceiling + 10 - y0)) : state;
                        level.setBlock(pos.set(x, y, z), placed, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Top of the column's last run plus one, or Integer.MIN_VALUE where the column is empty or outside the zone. */
    int columnTop(int x, int z) {
        int gx = x - x0, gz = z - z0;
        if (!filled(gx, gz)) return Integer.MIN_VALUE;
        int last = start[gx * sz + gz + 1] - 3;
        return y0 + runs[last] + runs[last + 1];
    }

    public boolean holdsAtOrBelow(int x, int y, int z) {
        int gx = x - x0, gz = z - z0;
        if (gx < 0 || gz < 0 || gx >= sx || gz >= sz) return false;
        int column = gx * sz + gz;
        return start[column] < start[column + 1] && y0 + runs[start[column]] <= y;
    }

    public boolean allows(double x, double y, double z) {
        int gx = (int) Math.floor(x) - x0 - copy(x, x0, sx), gz = (int) Math.floor(z) - z0 - copy(z, z0, sz);
        return y <= ceiling && gx >= 0 && gz >= 0 && gx < sx && gz < sz && walk[gx * sz + gz] != 0;
    }

    public Vec3 standOn(BlockPos pos) {
        VoxelShape shape = stateAt(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
        return Vec3.atBottomCenterOf(pos).add(0, shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y), 0);
    }

    private BlockState stateAt(BlockPos pos) {
        int gx = pos.getX() - x0, gz = pos.getZ() - z0;
        if (gx < 0 || gz < 0 || gx >= sx || gz >= sz) return Blocks.AIR.defaultBlockState();
        int column = gx * sz + gz;
        for (int r = start[column]; r < start[column + 1]; r += 3) {
            int bottom = y0 + runs[r];
            if (pos.getY() >= bottom && pos.getY() < bottom + runs[r + 1]) return palette[runs[r + 2]];
        }
        return Blocks.AIR.defaultBlockState();
    }

    public int ceiling() {
        return ceiling;
    }

    public BlockPos arrival() {
        return arrival;
    }

    public @Nullable BlockPos spot() {
        return spot;
    }

    public @Nullable Exit exit() {
        return exit;
    }

    public float yaw() {
        return yaw;
    }
}

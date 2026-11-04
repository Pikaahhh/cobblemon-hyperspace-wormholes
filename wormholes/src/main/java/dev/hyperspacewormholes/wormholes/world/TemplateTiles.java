package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Reads the vanilla structure templates that hold the zones' and crater formations' blocks, for Zone and BlockTemplate.
 * A template is one .nbt in data/hyperspace_wormholes/structure/FOLDER/NAME.nbt, or X_Y_Z tiles in FOLDER/NAME/ when a side passes 48 blocks; a .dat file next to it holds what a template can't (the tile list, shift, the fields Zone or BlockTemplate keeps).
 * Templates are read straight from the unzipped bytes with no tag per block, so a 700,000 block zone costs its bytes and little else, and nothing outside the call is touched, which keeps worldgen threads safe.
 */
final class TemplateTiles {
    static final int SIDE = 48;
    private static final String ROOT = "/data/hyperspace_wormholes/";
    private static final int INT = 3, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    interface Blocks {
        void at(int x, int y, int z, int state);
    }

    /** A zone's blocks: runs of (y, length, palette index) from start[column] to start[column + 1]. */
    record Columns(String[] palette, int[] start, short[] runs) {}

    private TemplateTiles() {}

    static CompoundTag data(String folder, String name) throws IOException {
        String path = ROOT + folder + "/" + name + ".dat";
        try (InputStream raw = TemplateTiles.class.getResourceAsStream(path)) {
            if (raw == null) throw new IOException("missing " + path);
            return NbtIo.readCompressed(raw, NbtAccounter.unlimitedHeap());
        }
    }

    /** The name of the tile at tiles[i], as a structure block takes it. */
    static String tile(String folder, String name, CompoundTag data, int i) {
        int[] tiles = data.getIntArray("tiles");
        return folder + "/" + name + (data.getBoolean("whole") ? "" : "/" + tiles[i] + "_" + tiles[i + 1] + "_" + tiles[i + 2]);
    }

    /** Hands every block of every tile to blocks and gives the palette, states as "id[key=value,...]". */
    static String[] read(String folder, String name, CompoundTag data, Blocks blocks) throws IOException {
        int[] tiles = data.getIntArray("tiles"), shift = data.getIntArray("shift");
        String[] palette = null;
        byte[][] buffer = {new byte[1 << 16]};
        for (int i = 0; i < tiles.length; i += 3) {
            String path = ROOT + "structure/" + tile(folder, name, data, i) + ".nbt";
            String[] own = tile(path, buffer, tiles[i] * SIDE - shift[0], tiles[i + 1] * SIDE, tiles[i + 2] * SIDE - shift[1], blocks);
            if (palette != null && !Arrays.equals(own, palette)) throw new IOException("palette differs in " + path);
            palette = own;
        }
        if (palette == null) throw new IOException("no tiles for " + name);
        return palette;
    }

    /**
     * Rebuilds a zone's column runs bottom up, a run per stretch of one palette index. The few columns not kept in that
     * order (data "unsorted": column, run count, then the runs) come back as they were, since holdsAtOrBelow and carving
     * read a column's first and last run. "bottom" is the y of the template's floor counted from the zone's corner, below
     * it for the roots under Cliff and Crag World's land, and "height" how tall the template is.
     */
    static Columns columns(String folder, String name, CompoundTag data) throws IOException {
        int[] size = data.getIntArray("size");
        int sx = size[0], sz = size[2], height = data.getInt("height"), bottom = data.getInt("bottom");
        short[] cells = new short[sx * sz * height];
        String[] palette = read(folder, name, data, (x, y, z, state) -> cells[(x * sz + z) * height + y] = (short) (state + 1));
        int[] unsorted = data.getIntArray("unsorted");
        int[] start = new int[sx * sz + 1];
        short[] runs = new short[1 << 16];
        int used = 0, next = 0;
        for (int column = 0; column < sx * sz; column++) {
            start[column] = used;
            if (used + 3 * height > runs.length) runs = Arrays.copyOf(runs, Math.max(runs.length * 2, used + 3 * height));
            if (next < unsorted.length && unsorted[next] == column) {
                int count = unsorted[next + 1] * 3;
                for (int i = 0; i < count; i++) runs[used++] = (short) unsorted[next + 2 + i];
                next += 2 + count;
                continue;
            }
            int base = column * height;
            for (int y = 0; y < height; ) {
                short cell = cells[base + y];
                int top = y + 1;
                while (top < height && cells[base + top] == cell) top++;
                if (cell != 0) {
                    runs[used++] = (short) (y + bottom);
                    runs[used++] = (short) (top - y);
                    runs[used++] = (short) (cell - 1);
                }
                y = top;
            }
        }
        start[sx * sz] = used;
        return new Columns(palette, start, Arrays.copyOf(runs, used));
    }

    private static String[] tile(String path, byte[][] buffer, int ox, int oy, int oz, Blocks blocks) throws IOException {
        ByteBuffer in = unzip(path, buffer);
        if (in.get() != COMPOUND) throw new IOException("not a template: " + path);
        skipName(in);
        String[] palette = null;
        for (int type = in.get(); type != 0; type = in.get()) {
            String key = text(in);
            if (key.equals("palette")) {
                palette = palette(in);
            } else if (key.equals("blocks")) {
                in.get();
                for (int left = in.getInt(); left > 0; left--) block(in, ox, oy, oz, blocks);
            } else {
                skip(in, type);
            }
        }
        if (palette == null) throw new IOException("no palette in " + path);
        return palette;
    }

    /** Unzips into buffer[0], kept for the next tile and grown as needed: a tile runs to 4 MB unzipped. */
    private static ByteBuffer unzip(String path, byte[][] buffer) throws IOException {
        try (InputStream raw = TemplateTiles.class.getResourceAsStream(path)) {
            if (raw == null) throw new IOException("missing " + path);
            InputStream in = new GZIPInputStream(raw);
            int used = 0;
            for (int got; (got = in.read(buffer[0], used, buffer[0].length - used)) >= 0; used += got) {
                if (used + got == buffer[0].length) buffer[0] = Arrays.copyOf(buffer[0], buffer[0].length * 2);
            }
            return ByteBuffer.wrap(buffer[0], 0, used);
        }
    }

    private static void block(ByteBuffer in, int ox, int oy, int oz, Blocks blocks) {
        int x = 0, y = 0, z = 0, state = 0;
        for (int type = in.get(); type != 0; type = in.get()) {
            skipName(in);
            if (type == LIST) {
                in.position(in.position() + 5);
                x = in.getInt();
                y = in.getInt();
                z = in.getInt();
            } else if (type == INT) {
                state = in.getInt();
            } else {
                skip(in, type);
            }
        }
        blocks.at(ox + x, oy + y, oz + z, state);
    }

    private static String[] palette(ByteBuffer in) {
        in.get();
        String[] palette = new String[in.getInt()];
        for (int i = 0; i < palette.length; i++) {
            String id = "";
            StringBuilder properties = new StringBuilder();
            for (int type = in.get(); type != 0; type = in.get()) {
                String key = text(in);
                if (key.equals("Name")) {
                    id = text(in);
                } else if (key.equals("Properties")) {
                    for (int inner = in.get(); inner != 0; inner = in.get()) {
                        properties.append(properties.isEmpty() ? '[' : ',').append(text(in)).append('=').append(text(in));
                    }
                    properties.append(']');
                } else {
                    skip(in, type);
                }
            }
            palette[i] = id + properties;
        }
        return palette;
    }

    private static void skip(ByteBuffer in, int type) {
        switch (type) {
            case 1 -> in.get();
            case 2 -> in.getShort();
            case INT, 5 -> in.getInt();
            case 4, 6 -> in.getLong();
            case BYTE_ARRAY -> in.position(in.getInt() + in.position());
            case STRING -> skipName(in);
            case LIST -> {
                int inner = in.get();
                for (int left = in.getInt(); left > 0; left--) skip(in, inner);
            }
            case COMPOUND -> {
                for (int inner = in.get(); inner != 0; inner = in.get()) {
                    skipName(in);
                    skip(in, inner);
                }
            }
            case INT_ARRAY -> in.position(in.getInt() * 4 + in.position());
            case LONG_ARRAY -> in.position(in.getInt() * 8 + in.position());
            default -> throw new IllegalStateException("bad tag type " + type);
        }
    }

    private static void skipName(ByteBuffer in) {
        in.position((in.getShort() & 0xFFFF) + in.position());
    }

    private static String text(ByteBuffer in) {
        int length = in.getShort() & 0xFFFF;
        String text = new String(in.array(), in.position(), length, StandardCharsets.UTF_8);
        in.position(in.position() + length);
        return text;
    }
}

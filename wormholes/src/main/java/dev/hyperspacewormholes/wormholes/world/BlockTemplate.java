package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A block grid made from one of the game's Ultra Space models, kept as a vanilla structure template (TemplateTiles),
 * with size, origin and stand point in its &lt;name&gt;.dat, and held as one byte per cell, indexed (x * sy + y) * sz + z. A
 * cell's value is its template palette index plus 1, and what it means is up to the feature using it (the palette shows
 * the block that value mostly places); 0 is always air and left out of the template. The origin is the foot of the
 * model, which goes at the placement's base.
 */
record BlockTemplate(int sx, int sy, int sz, int ox, int oy, int oz, byte[] cells) {
    static final byte AIR = 0;

    static BlockTemplate load(String folder, String name) {
        try {
            CompoundTag data = TemplateTiles.data(folder, name);
            int[] size = data.getIntArray("size"), origin = data.getIntArray("origin");
            int sx = size[0], sy = size[1], sz = size[2];
            byte[] cells = new byte[sx * sy * sz];
            TemplateTiles.read(folder, name, data, (x, y, z, state) -> cells[(x * sy + y) * sz + z] = (byte) (state + 1));
            BlockTemplate template = new BlockTemplate(sx, sy, sz, origin[0], origin[1], origin[2], cells);
            return template;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte at(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return AIR;
        return cells[(x * sy + y) * sz + z];
    }

    boolean filled(int column) {
        for (int y = 0; y < sy; y++) {
            if (at(column / sz, y, column % sz) != AIR) return true;
        }
        return false;
    }

    int reach() {
        return Math.max(Math.max(ox, sx - 1 - ox), Math.max(oz, sz - 1 - oz)) + 1;
    }

    /**
     * The column (x * sz + z) of this template that world column wx, wz falls in for a placement at x, z turned a
     * quarter turn `turns` times and maybe mirrored, or -1 outside it.
     */
    static int local(int wx, int wz, int x, int z, int turns, boolean mirror, int ox, int oz, int sx, int sz) {
        int dx = wx - x, dz = wz - z;
        for (int i = 0; i < turns; i++) {
            int r = dz;
            dz = -dx;
            dx = r;
        }
        if (mirror) dx = -dx;
        int tx = ox + dx, tz = oz + dz;
        if (tx < 0 || tz < 0 || tx >= sx || tz >= sz) return -1;
        return tx * sz + tz;
    }

    int column(int wx, int wz, int x, int z, int turns, boolean mirror) {
        return local(wx, wz, x, z, turns, mirror, ox, oz, sx, sz);
    }
}

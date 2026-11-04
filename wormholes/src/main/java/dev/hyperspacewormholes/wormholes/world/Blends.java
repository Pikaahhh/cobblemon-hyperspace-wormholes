package dev.hyperspacewormholes.wormholes.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Natural colour variation for the blocks of the Ultra Space Wilds' zones, so a cliff built from one block isn't one flat
 * colour. data/hyperspace_wormholes/blends.json gives, under "structures", a ladder of similar natural blocks from dark
 * to light for a block. Each block takes a rung from its height, shifted by slow 3D noise into patches and a little
 * per-block speckle; properties such as a stair's facing carry over, and blocks with no ladder are left alone.
 */
public final class Blends {
    private static final String PATH = "/data/hyperspace_wormholes/blends.json";
    private static final double PATCH = 9, PATCH_SPREAD = 1.4, SPECKLE = 0.7;
    private static final Map<Block, Block[]> LADDERS = new HashMap<>();

    static {
        load();
    }

    private Blends() {}

    public static BlockState pick(BlockState state, int x, int y, int z, double height) {
        Block[] ladder = LADDERS.get(state.getBlock());
        if (ladder == null) return state;
        int n = ladder.length;
        double g = Mth.clamp(height, 0, 1) * (n - 1)
                + (noise(x / PATCH, y / PATCH, z / PATCH) - 0.5) * PATCH_SPREAD
                + (CellFeature.unit(CellFeature.mix(x * 73856093L ^ y * 19349663L ^ z * 83492791L)) - 0.5) * SPECKLE;
        Block block = ladder[Mth.clamp((int) Math.round(g), 0, n - 1)];
        return block == state.getBlock() ? state : block.withPropertiesOf(state);
    }

    private static double noise(double x, double y, double z) {
        int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c00 = Mth.lerp(fx, corner(x0, y0, z0), corner(x0 + 1, y0, z0));
        double c10 = Mth.lerp(fx, corner(x0, y0 + 1, z0), corner(x0 + 1, y0 + 1, z0));
        double c01 = Mth.lerp(fx, corner(x0, y0, z0 + 1), corner(x0 + 1, y0, z0 + 1));
        double c11 = Mth.lerp(fx, corner(x0, y0 + 1, z0 + 1), corner(x0 + 1, y0 + 1, z0 + 1));
        return Mth.lerp(fz, Mth.lerp(fy, c00, c10), Mth.lerp(fy, c01, c11));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double corner(int x, int y, int z) {
        return CellFeature.unit(CellFeature.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L));
    }

    private static void load() {
        try (InputStream raw = Blends.class.getResourceAsStream(PATH)) {
            if (raw == null) throw new IOException("missing " + PATH);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("structures").entrySet()) {
                JsonArray rungs = entry.getValue().getAsJsonArray();
                Block[] ladder = new Block[rungs.size()];
                for (int i = 0; i < ladder.length; i++) ladder[i] = block(rungs.get(i).getAsString());
                LADDERS.put(block(entry.getKey()), ladder);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Block block(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("unknown block " + id + " in " + PATH);
        return BuiltInRegistries.BLOCK.get(key);
    }
}

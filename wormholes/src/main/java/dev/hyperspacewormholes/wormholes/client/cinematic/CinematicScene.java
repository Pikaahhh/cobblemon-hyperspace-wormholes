package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.Optional;

/**
 * One cutscene camera taken from the game (assets/hyperspace_wormholes/cinematics/<name>.json, baked from the USUM
 * cutscene's camera track). Per 30 fps frame: the camera's place in game units, its yaw, pitch and roll, its vertical field
 * of view, and which way the on-screen Pokemon faces and where it is. Each shot says what its camera is framed on (the
 * Pokemon's floor spot, +Z its facing, or the player's feet, +Z looking at the Pokemon) and how many blocks a game unit is.
 * An 11th field, when there, is how high Ultra Necrozma hovers over the floor. sample blends frame to frame in straight
 * lines; smoothSample runs a Catmull-Rom curve through the frames instead (never across a cut).
 */
public final class CinematicScene {
    public final int frames;
    public final float playerDistance;
    private final float[][] samples;
    private final boolean[] cut;
    private final int[] shotFrom;
    private final boolean[] shotOnPlayer;
    private final float[] shotScale;

    private CinematicScene(JsonObject json) {
        frames = json.get("frames").getAsInt();
        playerDistance = json.get("playerDistance").getAsFloat();
        JsonArray rows = json.getAsJsonArray("samples");
        samples = new float[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            JsonArray row = rows.get(i).getAsJsonArray();
            samples[i] = new float[row.size()];
            for (int k = 0; k < row.size(); k++) samples[i][k] = row.get(k).getAsFloat();
        }
        cut = new boolean[samples.length + 1];
        for (JsonElement c : json.getAsJsonArray("cuts")) {
            int f = c.getAsInt();
            if (f >= 0 && f < cut.length) cut[f] = true;
        }
        JsonArray shots = json.getAsJsonArray("shots");
        shotFrom = new int[shots.size()];
        shotOnPlayer = new boolean[shots.size()];
        shotScale = new float[shots.size()];
        for (int i = 0; i < shots.size(); i++) {
            JsonObject s = shots.get(i).getAsJsonObject();
            shotFrom[i] = s.get("from").getAsInt();
            shotOnPlayer[i] = s.get("anchor").getAsString().equals("player");
            shotScale[i] = s.get("scale").getAsFloat();
        }
    }

    public static @Nullable CinematicScene load(String name) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(Wormholes.id("cinematics/" + name + ".json"));
        if (resource.isEmpty()) {
            Wormholes.LOGGER.warn("No cinematic {}", name);
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return new CinematicScene(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Wormholes.LOGGER.error("Couldn't read cinematic {}", name, e);
            return null;
        }
    }

    public int shot(double frame) {
        int shot = 0;
        for (int i = 0; i < shotFrom.length; i++) {
            if (frame >= shotFrom[i]) shot = i;
        }
        return shot;
    }

    public int shotStart(int shot) {
        return shotFrom[shot];
    }

    public int shotEnd(int shot) {
        return shot + 1 < shotFrom.length ? shotFrom[shot + 1] : frames + 1;
    }

    public boolean onPlayer(double frame) {
        return shotOnPlayer[shot(frame)];
    }

    public float scale(double frame) {
        return shotScale[shot(frame)];
    }

    public float hover(double frame) {
        double f = Mth.clamp(frame, 0, samples.length - 1);
        int i = (int) Math.floor(f);
        float[] a = samples[i];
        if (a.length <= 10) return 0;
        if (i >= samples.length - 1 || cut[i + 1]) return a[10];
        return Mth.lerp((float) (f - i), a[10], samples[i + 1][10]);
    }

    public float[] sample(double frame) {
        double f = Mth.clamp(frame, 0, samples.length - 1);
        int i = (int) Math.floor(f);
        if (i >= samples.length - 1 || cut[i + 1]) return samples[i].clone();
        float t = (float) (f - i);
        float[] a = samples[i], b = samples[i + 1], out = new float[a.length];
        for (int k = 0; k < a.length; k++) {
            boolean angle = k >= 3 && k <= 5 || k == 7;
            out[k] = angle ? a[k] + Mth.wrapDegrees(b[k] - a[k]) * t : Mth.lerp(t, a[k], b[k]);
        }
        return out;
    }

    public float[] smoothSample(double frame) {
        double f = Mth.clamp(frame, 0, samples.length - 1);
        int i = (int) Math.floor(f);
        if (i >= samples.length - 1 || cut[i + 1]) return samples[i].clone();
        float t = (float) (f - i), t2 = t * t, t3 = t2 * t;
        float[] a = samples[i], b = samples[i + 1], out = new float[a.length];
        float[] p = i > 0 && !cut[i] ? samples[i - 1] : a;
        float[] n = i + 2 < samples.length && !cut[i + 2] ? samples[i + 2] : b;
        for (int k = 0; k < a.length; k++) {
            boolean angle = k >= 3 && k <= 5 || k == 7;
            float pk = angle ? a[k] + Mth.wrapDegrees(p[k] - a[k]) : p[k];
            float bk = angle ? a[k] + Mth.wrapDegrees(b[k] - a[k]) : b[k];
            float nk = angle ? bk + Mth.wrapDegrees(n[k] - b[k]) : n[k];
            out[k] = 0.5f * (2 * a[k] + (bk - pk) * t + (2 * pk - 5 * a[k] + 4 * bk - nk) * t2
                    + (3 * a[k] - pk - 3 * bk + nk) * t3);
        }
        return out;
    }
}

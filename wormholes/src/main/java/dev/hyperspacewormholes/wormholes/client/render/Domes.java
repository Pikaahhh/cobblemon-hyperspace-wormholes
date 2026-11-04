package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * Draws plain coloured spheres, used round a battle to black out everything beyond them (Light That Burns the Sky's
 * darkness). The sines and cosines of its 25 latitudes and 49 longitudes are worked out once, since the darkness draws
 * six spheres a frame (27,648 vertices) and per-vertex trig was a noticeable share of the render thread.
 */
public final class Domes {
    private static final int RINGS = 24, SEGMENTS = 48;
    private static final float[] COS_LAT = new float[RINGS + 1], SIN_LAT = new float[RINGS + 1];
    private static final float[] COS_LON = new float[SEGMENTS + 1], SIN_LON = new float[SEGMENTS + 1];

    static {
        for (int k = 0; k <= RINGS; k++) {
            double lat = Math.PI * k / RINGS - Math.PI / 2;
            COS_LAT[k] = (float) Math.cos(lat);
            SIN_LAT[k] = (float) Math.sin(lat);
        }
        for (int k = 0; k <= SEGMENTS; k++) {
            double lon = Math.PI * 2 * k / SEGMENTS;
            COS_LON[k] = (float) Math.cos(lon);
            SIN_LON[k] = (float) Math.sin(lon);
        }
    }

    private Domes() {}

    public static void sphere(VertexConsumer out, Vector3f centre, float radius, float r, float g, float b, float a) {
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                point(out, centre, radius, i, j, r, g, b, a);
                point(out, centre, radius, i + 1, j, r, g, b, a);
                point(out, centre, radius, i + 1, j + 1, r, g, b, a);
                point(out, centre, radius, i, j + 1, r, g, b, a);
            }
        }
    }

    private static void point(VertexConsumer out, Vector3f centre, float radius, int lat, int lon,
                              float r, float g, float b, float a) {
        float ring = COS_LAT[lat] * radius;
        out.addVertex(centre.x + ring * COS_LON[lon], centre.y + SIN_LAT[lat] * radius,
                centre.z + ring * SIN_LON[lon]).setColor(r, g, b, a);
    }
}

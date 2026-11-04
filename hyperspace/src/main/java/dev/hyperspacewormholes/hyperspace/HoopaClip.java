package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Hides the part of a Pokemon that hasn't come through a Hoopa ring yet: while the server's Clip payload is on for an
 * entity, PokemonRendererClipMixin wraps the buffers Cobblemon draws it with so every quad is cut by the ring's plane.
 * The pose stack is camera-relative, so the plane is moved into those coordinates (ring centre minus camera) at begin.
 */
public class HoopaClip {
    /** A plane in world space: points with (p - point) . normal < 0 are hidden. */
    private record Plane(Vec3 point, Vec3 normal, long until) {
    }

    private static final Map<Integer, Plane> planes = new HashMap<>();
    private static final long LONGEST = 20 * 60;
    /** The plane of the entity being drawn, camera-relative; null when it has none. */
    private static float[] current;
    private static ClipSource source;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HoopaSummon.Clip.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (!payload.on()) {
                planes.remove(payload.entity());
                return;
            }
            Vec3 normal = new Vec3(payload.nx(), payload.ny(), payload.nz()).normalize();
            planes.put(payload.entity(), new Plane(new Vec3(payload.x(), payload.y(), payload.z()), normal,
                    mc.level.getGameTime() + LONGEST));
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) {
                planes.clear();
                return;
            }
            long now = mc.level.getGameTime();
            planes.values().removeIf(p -> p.until < now);
        });
    }

    public static void begin(Entity entity, Vec3 camera) {
        current = null;
        if (planes.isEmpty() || entity == null) return;
        Plane plane = planes.get(entity.getId());
        if (plane == null) return;
        current = new float[]{(float) (plane.point.x - camera.x), (float) (plane.point.y - camera.y),
                (float) (plane.point.z - camera.z), (float) plane.normal.x, (float) plane.normal.y, (float) plane.normal.z};
    }

    public static void end() {
        if (source != null) source.flush();
        source = null;
        current = null;
    }

    public static MultiBufferSource wrap(MultiBufferSource buffers) {
        if (current == null) return buffers;
        source = new ClipSource(buffers, current);
        return source;
    }

    /**
     * Clips the entity-model buffers and leaves the rest alone. A consumer holds its last quad back, so it's flushed
     * first.
     */
    private static class ClipSource implements MultiBufferSource {
        private final MultiBufferSource buffers;
        private final float[] plane;
        private ClipConsumer last;

        ClipSource(MultiBufferSource buffers, float[] plane) {
            this.buffers = buffers;
            this.plane = plane;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            flush();
            VertexConsumer buffer = buffers.getBuffer(type);
            if (type.mode() != VertexFormat.Mode.QUADS || type.format() != DefaultVertexFormat.NEW_ENTITY) return buffer;
            last = new ClipConsumer(buffer, plane);
            return last;
        }

        void flush() {
            if (last != null) last.flush();
            last = null;
        }
    }

    /**
     * Cuts each quad by the plane (Sutherland-Hodgman) and passes on what is in front: the whole quad, nothing, or the
     * cut polygon as one or two quads (a repeated last corner closes a triangle). Colour, uv and normal are
     * interpolated along cut edges.
     */
    static class ClipConsumer implements VertexConsumer {
        private static final int COLOR = 1, UV0 = 2, UV1 = 4, UV2 = 8, NORMAL = 16;

        private static class V {
            float x, y, z, u, v, nx, ny, nz, d;
            int color = -1, uv1, uv2, mask;

            void copy(V o) {
                x = o.x; y = o.y; z = o.z; u = o.u; v = o.v; nx = o.nx; ny = o.ny; nz = o.nz;
                d = o.d; color = o.color; uv1 = o.uv1; uv2 = o.uv2; mask = o.mask;
            }
        }

        private final VertexConsumer out;
        private final float px, py, pz, nx, ny, nz;
        private final V[] quad = {new V(), new V(), new V(), new V()};
        private final V[] poly = new V[8];
        private int count;
        private V at;

        ClipConsumer(VertexConsumer out, float[] plane) {
            this.out = out;
            px = plane[0]; py = plane[1]; pz = plane[2]; nx = plane[3]; ny = plane[4]; nz = plane[5];
            for (int i = 0; i < poly.length; i++) poly[i] = new V();
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (count == 4) emit();
            at = quad[count++];
            at.mask = 0;
            at.color = -1;
            at.x = x; at.y = y; at.z = z;
            at.d = (x - px) * nx + (y - py) * ny + (z - pz) * nz;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            if (at != null) { at.color = argb; at.mask |= COLOR; }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (at != null) { at.u = u; at.v = v; at.mask |= UV0; }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (at != null) { at.uv1 = (u & 0xFFFF) | v << 16; at.mask |= UV1; }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (at != null) { at.uv2 = (u & 0xFFFF) | v << 16; at.mask |= UV2; }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (at != null) { at.nx = x; at.ny = y; at.nz = z; at.mask |= NORMAL; }
            return this;
        }

        void flush() {
            if (count == 4) emit();
            // a partial quad can't be cut; pass it on as it came
            for (int i = 0; i < count; i++) put(quad[i]);
            count = 0;
            at = null;
        }

        private void emit() {
            count = 0;
            at = null;
            boolean anyIn = false, anyOut = false;
            for (V v : quad) {
                if (v.d >= 0) anyIn = true;
                else anyOut = true;
            }
            if (!anyIn) return;
            if (!anyOut) {
                for (V v : quad) put(v);
                return;
            }
            int n = 0;
            for (int i = 0; i < 4; i++) {
                V a = quad[i], b = quad[(i + 1) % 4];
                if (a.d >= 0) poly[n++].copy(a);
                if ((a.d >= 0) != (b.d >= 0)) lerp(poly[n++], a, b, a.d / (a.d - b.d));
            }
            if (n < 3) return;
            put(poly[0]); put(poly[1]); put(poly[2]); put(poly[n >= 4 ? 3 : 2]);
            if (n == 5) {
                put(poly[0]); put(poly[3]); put(poly[4]); put(poly[4]);
            }
        }

        private static void lerp(V into, V a, V b, float t) {
            V keep = a.d >= 0 ? a : b;
            into.copy(keep);
            into.x = a.x + (b.x - a.x) * t;
            into.y = a.y + (b.y - a.y) * t;
            into.z = a.z + (b.z - a.z) * t;
            into.u = a.u + (b.u - a.u) * t;
            into.v = a.v + (b.v - a.v) * t;
            into.color = mix(a.color, b.color, t);
            into.d = 0;
        }

        private static int mix(int a, int b, float t) {
            int out = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int ca = a >>> shift & 255, cb = b >>> shift & 255;
                out |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return out;
        }

        private void put(V v) {
            out.addVertex(v.x, v.y, v.z);
            if ((v.mask & COLOR) != 0) out.setColor(v.color);
            if ((v.mask & UV0) != 0) out.setUv(v.u, v.v);
            if ((v.mask & UV1) != 0) out.setUv1(v.uv1 & 0xFFFF, v.uv1 >>> 16);
            if ((v.mask & UV2) != 0) out.setUv2(v.uv2 & 0xFFFF, v.uv2 >>> 16);
            if ((v.mask & NORMAL) != 0) out.setNormal(v.nx, v.ny, v.nz);
        }
    }
}

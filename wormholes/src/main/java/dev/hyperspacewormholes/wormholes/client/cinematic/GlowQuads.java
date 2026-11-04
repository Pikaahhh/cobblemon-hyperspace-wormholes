package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.client.render.WarpRenderTypes;
import dev.hyperspacewormholes.wormholes.client.render.WarpTextures;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Additive glow quads in the world for the Necrozma cutscenes' light (UltraBurstEffects, PowerUpEffects and the
 * defeat): camera-facing billboards of the soft glow texture, flat-coloured rays and ribbons, all gathered for one
 * frame against the camera's position and drawn at once.
 */
final class GlowQuads {
    private static final int VERTEX = 9;
    private final Vec3 cam, right, up;
    private float[] data = new float[VERTEX * 4 * 64];
    private int size;

    GlowQuads(WorldRenderContext context) {
        cam = context.camera().getPosition();
        Quaternionf orientation = context.camera().rotation();
        Vector3f r = orientation.transform(new Vector3f(1, 0, 0)), u = orientation.transform(new Vector3f(0, 1, 0));
        right = new Vec3(r.x, r.y, r.z);
        up = new Vec3(u.x, u.y, u.z);
    }

    Vec3 right() {
        return right;
    }

    Vec3 up() {
        return up;
    }

    Vec3 cam() {
        return cam;
    }

    private void vertex(Vec3 p, float u, float v, float r, float g, float b, float a) {
        if (size + VERTEX > data.length) data = Arrays.copyOf(data, data.length * 2);
        float[] d = data;
        int o = size;
        d[o] = (float) (p.x - cam.x);
        d[o + 1] = (float) (p.y - cam.y);
        d[o + 2] = (float) (p.z - cam.z);
        d[o + 3] = u;
        d[o + 4] = v;
        d[o + 5] = r;
        d[o + 6] = g;
        d[o + 7] = b;
        d[o + 8] = a;
        size = o + VERTEX;
    }

    /** A flat-coloured quad (the middle of the glow texture), its alpha given per corner. */
    void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float alphaA, float alphaB, float alphaC, float alphaD) {
        vertex(a, 0.5f, 0.5f, r, g, bl, alphaA);
        vertex(b, 0.5f, 0.5f, r, g, bl, alphaB);
        vertex(c, 0.5f, 0.5f, r, g, bl, alphaC);
        vertex(d, 0.5f, 0.5f, r, g, bl, alphaD);
    }

    void textured(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float alpha) {
        vertex(a, 0, 1, r, g, bl, alpha);
        vertex(b, 1, 1, r, g, bl, alpha);
        vertex(c, 1, 0, r, g, bl, alpha);
        vertex(d, 0, 0, r, g, bl, alpha);
    }

    /** A camera-facing glow, drawn whatever its size and alpha. */
    void sprite(Vec3 at, double size, float r, float g, float b, float alpha) {
        Vec3 rx = right.scale(size), uy = up.scale(size);
        textured(at.subtract(rx).subtract(uy), at.add(rx).subtract(uy), at.add(rx).add(uy), at.subtract(rx).add(uy), r, g, b, alpha);
    }

    /** A camera-facing glow, skipped when it would barely show. */
    void billboard(Vec3 at, double size, float r, float g, float b, float alpha) {
        if (alpha <= 0.002f || size <= 0) return;
        sprite(at, size, r, g, b, alpha);
    }

    void ray(Vec3 from, Vec3 to, double width, float r, float g, float b, float alphaFrom, float alphaTo) {
        Vec3 side = to.subtract(from).cross(from.subtract(cam));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width);
        quad(from.add(side), from.subtract(side), to, to, r, g, b, alphaFrom, alphaFrom, alphaTo, alphaTo);
    }

    void ribbon(Vec3[] trail, double width, float r, float g, float b, float alpha) {
        for (int t = 0; t < trail.length - 1; t++) {
            Vec3 a = trail[t], c = trail[t + 1];
            Vec3 side = c.subtract(a).cross(a.subtract(cam));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize();
            float k0 = 1 - t / (float) (trail.length - 1), k1 = 1 - (t + 1) / (float) (trail.length - 1);
            float w0 = (float) Math.sin(Math.PI * k0), w1 = (float) Math.sin(Math.PI * k1);
            Vec3 s0 = side.scale(width * (0.3 + 0.7 * w0)), s1 = side.scale(width * (0.3 + 0.7 * w1));
            quad(a.add(s0), a.subtract(s0), c.subtract(s1), c.add(s1), r, g, b, alpha * w0, alpha * w0, alpha * w1, alpha * w1);
        }
    }

    void draw() {
        if (size == 0) return;
        WarpTextures.ensureReady();
        float[] d = data;
        RenderType type = WarpRenderTypes.additive(WarpTextures.GLOW);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(type);
        for (int o = 0; o < size; o += VERTEX) {
            buffer.addVertex(d[o], d[o + 1], d[o + 2]).setUv(d[o + 3], d[o + 4]).setColor(d[o + 5], d[o + 6], d[o + 7], d[o + 8]);
        }
        buffers.endBatch(type);
    }
}

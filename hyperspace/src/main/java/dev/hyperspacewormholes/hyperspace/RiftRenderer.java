package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Draws a Rift, Z-A's spatial distortion or Hoopa's portal opening, as pixel art flat on the wall. The textures in
 * textures/effect/rift are frame grids drawn at true size (16 pixels a block), so the hole can grow, spin and shrink
 * without a texel changing size; this only picks cells and nudges them by whole pixels. A distortion's label (name,
 * stars, types) fades in above it when you look at it from within LABEL_RANGE.
 */
public class RiftRenderer extends EntityRenderer<Rift> {
    private static final float PX = 1 / 16f;
    private static final float WALL = 0.28f;
    private static final int SPIN = 16;
    private static final int RGB_FRAMES = 8;
    private static final int GATE_PHASES = 4;
    private static final float SMALL = 14;
    private static final float OPEN_SCALE = 14.4f;
    private static final int POINT_PHASES = 24;
    private static final float LOCKED_SCALE = 0.4f;
    private static final double LABEL_RANGE = 7;
    private static final double LOOK_RADIUS = 13 * PX;
    private static final float LABEL_Y = 1;
    private static final float LABEL_OUT = -0.26f;
    private static final float LABEL_FADE = 4;
    private static final int LABEL_WIDTH = 200;
    private static final int LABEL_BACKGROUND = 0x40;
    private static final float[] WHITE_TINT = {1, 1, 1};
    private static final float[] MINT_TINT = {0.5f, 1, 0.71f};
    private static final float[] PINK_TINT = {1, 0.5f, 0.85f};
    private static final float[][] GROW = {{0, 0.3f}, {25, 1}};
    private static final float[][] HOLE_KEYS = {{0, 1}, {26, 1}, {34, 1.56f}, {46, 1.1f}, {70, 0.87f}, {74, 2.21f}, {91, 3f}, {122, 3.2f}};
    private static final float[][] GATE_COLOURS = {{0, 1, 0.278f, 0.792f}, {0.18f, 1, 0.835f, 1}, {0.31f, 1, 1, 1}};
    private static final float[][] RGB_TINTS = {{1, 0, 0}, {0.02f, 1, 0}, {0, 0.04f, 1}};
    private static final float[][] SPARKS = {{1, 0, 0}, {0, 0.83f, 0}, {0, 0, 1}, {1, 0.47f, 0.47f}, {0.47f, 1, 0.47f}, {0.47f, 0.55f, 1}};

    private static final Tex HOLE = new Tex("hole", 768, 1104, 48);
    private static final Tex HOLE_GLOW = new Tex("hole_glow", 768, 1104, 48);
    private static final Tex POINT = new Tex("point", 1152, 1104, 48);
    private static final Tex RGB = new Tex("rgb", 448, 672, 56);
    private static final Tex WAVE = new Tex("wave", 1024, 64, 64);
    private static final Tex GATE = new Tex("gate", 320, 960, 80);
    private static final Tex WHITE = new Tex("white", 4, 4, 4);

    private static final RenderStateShard.ShaderStateShard SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
    private static final Function<ResourceLocation, RenderType> LIGHT_TYPES = Util.memoize(texture ->
            type("hyperspace_rift_light", RenderStateShard.LIGHTNING_TRANSPARENCY, texture));
    private static final Function<ResourceLocation, RenderType> SHADE_TYPES = Util.memoize(texture ->
            type("hyperspace_rift_shade", RenderStateShard.TRANSLUCENT_TRANSPARENCY, texture));

    private record Tex(ResourceLocation id, int width, int height, int cell) {
        Tex(String name, int width, int height, int cell) {
            this(Hyperspace.id("textures/effect/rift/" + name + ".png"), width, height, cell);
        }
    }

    private record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float z, int colour) {
    }

    private static final class Layers {
        final Map<ResourceLocation, List<Quad>> shade = new LinkedHashMap<>();
        final Map<ResourceLocation, List<Quad>> light = new LinkedHashMap<>();

        void cell(boolean add, Tex tex, int col, int row, int dx, int dy, float z, float r, float g, float b, float a) {
            if (a <= 0.004f) return;
            float half = tex.cell() / 2f;
            float u0 = (col * tex.cell() + 0.01f) / tex.width(), u1 = ((col + 1) * tex.cell() - 0.01f) / tex.width();
            float v0 = (row * tex.cell() + 0.01f) / tex.height(), v1 = ((row + 1) * tex.cell() - 0.01f) / tex.height();
            add(add, tex, new Quad((dx - half) * PX, (dy - half) * PX, (dx + half) * PX, (dy + half) * PX, u0, v1, u1, v0, z,
                    colour(r, g, b, a)));
        }

        void rect(int x, int y, int w, int h, float z, float r, float g, float b, float a) {
            if (a <= 0.004f) return;
            add(true, WHITE, new Quad(x * PX, y * PX, (x + w) * PX, (y + h) * PX, 0, 1, 1, 0, z, colour(r, g, b, a)));
        }

        void add(boolean add, Tex tex, Quad quad) {
            (add ? light : shade).computeIfAbsent(tex.id(), id -> new ArrayList<>()).add(quad);
        }

        void draw(PoseStack pose, MultiBufferSource buffers) {
            Matrix4f m = pose.last().pose();
            shade.forEach((texture, quads) -> {
                VertexConsumer vc = buffers.getBuffer(SHADE_TYPES.apply(texture));
                for (Quad q : quads) put(m, vc, q);
            });
            light.forEach((texture, quads) -> {
                VertexConsumer vc = buffers.getBuffer(LIGHT_TYPES.apply(texture));
                for (Quad q : quads) put(m, vc, q);
            });
        }

        private static void put(Matrix4f m, VertexConsumer vc, Quad q) {
            vc.addVertex(m, q.x0(), q.y0(), q.z()).setUv(q.u0(), q.v0()).setColor(q.colour());
            vc.addVertex(m, q.x1(), q.y0(), q.z()).setUv(q.u1(), q.v0()).setColor(q.colour());
            vc.addVertex(m, q.x1(), q.y1(), q.z()).setUv(q.u1(), q.v1()).setColor(q.colour());
            vc.addVertex(m, q.x0(), q.y1(), q.z()).setUv(q.u0(), q.v1()).setColor(q.colour());
        }
    }

    public RiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    // dark parts blend over the wall and light parts add light; nearest sampled, depth tested, no depth write
    private static RenderType type(String name, RenderStateShard.TransparencyStateShard blend, ResourceLocation texture) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(blend)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    @Override
    public boolean shouldRender(Rift rift, Frustum frustum, double x, double y, double z) {
        return rift.shouldRender(x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(Rift rift) {
        return HOLE.id();
    }

    private static boolean lookedAt(Rift rift, float partialTicks) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null || rift.portal() || rift.ends() != 0) return false;
        Vec3 normal = Vec3.directionFromRotation(0, rift.getYRot());
        Vec3 middle = rift.position().subtract(normal.scale(WALL));
        Vec3 eye = viewer.getEyePosition(partialTicks);
        if (eye.distanceToSqr(middle) > LABEL_RANGE * LABEL_RANGE) return false;
        Vec3 look = viewer.getViewVector(partialTicks);
        double facing = look.dot(normal), along = middle.subtract(eye).dot(normal) / facing;
        if (facing >= 0 || along <= 0) return false;
        Vec3 hit = eye.add(look.scale(along));
        return hit.distanceToSqr(middle) < LOOK_RADIUS * LOOK_RADIUS && rift.level().clip(new ClipContext(eye, hit,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void render(Rift rift, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float tick = rift.level().getGameTime() + partialTicks;
        float time = tick - rift.since();
        float now = rift.level().getGameTime() % 24000 + partialTicks;
        float ended = rift.ends() == 0 ? -1 : tick - rift.ends();
        boolean stirred = rift.stirred() != 0 && tick - rift.stirred() < Rift.STIR;
        int seed = rift.getUUID().hashCode();
        boolean unstable = rift.unstable();
        float lockAge = rift.lockedAt() == 0 ? Float.MAX_VALUE : tick - rift.lockedAt();

        Layers layers = new Layers();
        float p = Mth.clamp(lockAge / Rift.LOCK_TIME, 0, 1);
        float size = rift.locked() ? Mth.lerp(p, 1, LOCKED_SCALE) : Mth.lerp(p, LOCKED_SCALE, 1);
        if (rift.portal()) portal(layers, time, now, ended, seed, unstable, size);
        else distortion(layers, time, now, ended < 0 ? Float.MAX_VALUE : Rift.FADE - ended, stirred, seed);
        int jx = 0, jy = 0;
        int beat = (int) (now / 2);
        if (unstable && hash(seed, beat, 8, 0) < 0.3f) {
            jx = hash(seed, beat, 9, 0) < 0.5f ? -1 : 1;
            jy = hash(seed, beat, 10, 0) < 0.5f ? -1 : 1;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-rift.getYRot()));
        pose.translate(jx * PX, jy * PX, -WALL);
        layers.draw(pose, buffers);
        pose.popPose();

        double exact = rift.level().getGameTime() + (double) partialTicks;
        rift.labelFade = Mth.clamp(rift.labelFade + (lookedAt(rift, partialTicks) ? 1 : -1) * (float) (exact - rift.labelAt) / LABEL_FADE, 0, 1);
        rift.labelAt = exact;
        if (rift.labelFade > 0) {
            List<FormattedCharSequence> lines = getFont().split(rift.label(), LABEL_WIDTH);
            if (!lines.isEmpty()) label(lines, rift.labelFade, rift.getYRot(), pose, buffers);
        }
        super.render(rift, entityYaw, partialTicks, pose, buffers, light);
    }

    private void label(List<FormattedCharSequence> lines, float fade, float riftYaw, PoseStack pose, MultiBufferSource buffers) {
        Font font = getFont();
        int width = 0;
        for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
        int height = lines.size() * (font.lineHeight + 1) - 1;
        float yaw = riftYaw * Mth.DEG_TO_RAD;
        pose.pushPose();
        pose.translate(-Mth.sin(yaw) * LABEL_OUT, LABEL_Y, Mth.cos(yaw) * LABEL_OUT);
        pose.mulPose(Axis.YP.rotationDegrees(-riftYaw));
        pose.scale(0.025f, -0.025f, 0.025f);
        pose.translate(1 - width / 2f, -height, 0);
        Matrix4f m = pose.last().pose();
        int background = Math.round(fade * LABEL_BACKGROUND) << 24;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        vc.addVertex(m, -1, -1, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, -1, height, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, width, height, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, width, -1, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        // Font turns a colour with almost no alpha opaque, which would flash at the ends of the fade
        int colour = Math.max(4, Math.round(fade * 255)) << 24 | 0xFFFFFF;
        float y = 0;
        for (FormattedCharSequence line : lines) {
            font.drawInBatch(line, (width - font.width(line)) / 2f, y, colour, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            y += font.lineHeight + 1;
        }
        pose.popPose();
    }

    private static void distortion(Layers l, float t, float now, float left, boolean stir, int seed) {
        float f = t * 1.5f;
        float fade = Mth.clamp(left / 100, 0, 1);
        int d = even(SMALL * keys(GROW, f) * Mth.clamp(left / 40, 0, 1));
        if (stir && (int) (t / 4) % 2 == 0) d += 2;
        rift(l, d, now, fade, 0.85f * Mth.clamp(f / 30, 0, 1) * fade, WHITE_TINT, 0.3f * Mth.clamp(f / 60, 0, 1) * fade, 1);
        if (f >= 50 && f < 110) {
            float p = (f - 50) / 60;
            wave(l, p, 0.6f * (1 - p) * fade, MINT_TINT);
        }
        sparks(l, t, stir ? 14 : 8, Math.max(d / 2f + 3, 8), seed, Mth.clamp((f - 50) / 20, 0, 1) * fade, 4);
    }

    private static void portal(Layers l, float t, float now, float closing, int seed, boolean unstable, float size) {
        float f = t * 1.5f;
        int d = Math.min(46, even(OPEN_SCALE * keys(HOLE_KEYS, f) * size));
        float fade = 1;
        if (closing >= 0) {
            float q = Mth.clamp(closing / Rift.CLOSING, 0, 1);
            d = even(d * (1 - q * q));
            fade = 1 - q;
        }
        float r = d / 2f;
        float[] tint = WHITE_TINT;
        float ring = 0.85f * fade;
        if (f >= 67) {
            float g = (f - 67) / 180;
            tint = new float[]{keys(GATE_COLOURS, g, 1), keys(GATE_COLOURS, g, 2), keys(GATE_COLOURS, g, 3)};
            ring = fade;
        }
        float rgbAlpha = 0.45f * fade;
        int wobble = Math.max(1, Math.round(r / 8));
        if (unstable) {
            if (hash(seed, (int) (now / 3), 11, 0) < 0.2f) {
                fade *= 0.5f;
                ring *= 0.5f;
            }
            rgbAlpha = Math.min(1, 0.85f * fade);
            wobble = Math.max(2, Math.round(r / 4));
        }
        rift(l, d, now, fade, ring, tint, rgbAlpha, wobble);
        if (f >= 26 && f < 86) {
            float p = (f - 26) / 60;
            wave(l, p, 0.7f * (1 - p) * fade, PINK_TINT);
        }
        if (f < 67) sparks(l, t, 8, Math.max(r + 3, 8), seed, fade, 4);
        else sparks(l, t, 40, Math.max(r - 2, 12), seed, fade, 2);
    }

    private static void rift(Layers l, int d, float now, float fade, float ring, float[] tint, float rgbAlpha, int wobble) {
        if (d < 2) return;
        hole(l, d, now, fade);
        rgb(l, d, now, rgbAlpha, wobble);
        glow(l, d, now, fade);
        l.cell(true, POINT, Math.floorMod((int) (now / 5), POINT_PHASES), Math.min(46, d) / 2 - 1, 0, 0, 0.03f, 1, 1, 1, fade);
        int r = d / 2;
        int in = Mth.clamp(r + (r % 2 == 1 ? 1 : 2), 2, 24);
        l.cell(true, GATE, Math.floorMod((int) (now / 6), GATE_PHASES), in / 2 - 1, 0, 0, 0.02f, tint[0], tint[1], tint[2], ring);
    }

    private static void hole(Layers l, int d, float t, float a) {
        if (d < 2) return;
        l.cell(false, HOLE, Math.floorMod((int) (t / 30), SPIN), Math.min(46, d) / 2 - 1, 0, 0, 0, 1, 1, 1, a);
    }

    private static void glow(Layers l, int d, float t, float a) {
        if (d < 2) return;
        l.cell(true, HOLE_GLOW, Math.floorMod((int) (t / 30), SPIN), Math.min(46, d) / 2 - 1, 0, 0, 0.015f, 1, 1, 1, a);
    }

    private static void rgb(Layers l, int d, float t, float a, int wobble) {
        int row = Mth.clamp(Math.round((d + 2 - 8) / 4f), 0, 11);
        for (int ch = 0; ch < 3; ch++) {
            float angle = t * 0.05f + ch * 2.094f;
            float[] tint = RGB_TINTS[ch];
            l.cell(true, RGB, Math.floorMod((int) (t / 4) + ch * 3, RGB_FRAMES), row, Math.round(Mth.cos(angle) * wobble),
                    Math.round(Mth.sin(angle) * wobble), 0.01f, tint[0], tint[1], tint[2], a);
        }
    }

    private static void wave(Layers l, float p, float a, float[] tint) {
        int r = Mth.clamp(even(2 + 30 * p), 2, 32);
        l.cell(true, WAVE, r / 2 - 1, 0, 0, 0, 0.02f, tint[0], tint[1], tint[2], a);
    }

    private static void sparks(Layers l, float t, int count, float radius, int seed, float a, int bigEvery) {
        if (a <= 0) return;
        for (int i = 0; i < count; i++) {
            float life = 20 + 50 * hash(seed, i, 1, 0);
            float shifted = t + hash(seed, i, 2, 0) * life;
            int n = Mth.floor(shifted / life);
            float local = (shifted - n * life) / life;
            float s = local < 0.2f ? local / 0.2f : (1 - local) / 0.8f;
            if (s < 0.3f) continue;
            float angle = hash(seed, i, n, 3) * Mth.TWO_PI;
            float reach = i % 3 != 0 ? radius : radius + 12;
            float rr = Mth.sqrt(hash(seed, i, n, 4)) * reach * (1 + 0.35f * local);
            float[] c = SPARKS[(int) (hash(seed, i, n, 5) * 6) % 6];
            int size = i % bigEvery == 0 && s > 0.45f ? (i % 3 == 0 ? 3 : 2) : 1;
            l.rect(Math.round(Mth.cos(angle) * rr) - size / 2, Math.round(Mth.sin(angle) * rr) - size / 2, size, size, 0.025f,
                    c[0], c[1], c[2], a);
        }
    }

    private static int even(float x) {
        return Math.round(x / 2) * 2;
    }

    private static float keys(float[][] points, float x) {
        return keys(points, x, 1);
    }

    private static float keys(float[][] points, float x, int value) {
        if (x <= points[0][0]) return points[0][value];
        for (int i = 1; i < points.length; i++) {
            if (x <= points[i][0]) {
                float[] a = points[i - 1], b = points[i];
                return a[value] + (b[value] - a[value]) * (x - a[0]) / (b[0] - a[0]);
            }
        }
        return points[points.length - 1][value];
    }

    private static float hash(int a, int b, int c, int d) {
        return (mix(mix(mix(mix(0, a), b), c), d) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static long mix(long n, int x) {
        n = (n * 31 + x * 0x9E3779B1L + 0x632BE5ABL) & 0xFFFFFFFFL;
        n ^= n >>> 15;
        n = (n * 0x2C1B3C6DL) & 0xFFFFFFFFL;
        return n ^ n >>> 12;
    }

    private static int colour(float r, float g, float b, float a) {
        return Mth.clamp(Math.round(a * 255), 0, 255) << 24 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}

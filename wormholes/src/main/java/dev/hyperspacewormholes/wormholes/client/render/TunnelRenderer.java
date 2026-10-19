package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.ride.TunnelLayout;
import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Draws Ultra Space during the ride, pixel-art style: the nebula and starry sky, the swirly vortex tunnel, wormholes,
 * golden rings and crystal bits along it, and the light trail behind you as you steer.
 */
public final class TunnelRenderer {
    private static final float Z_NEAR = -50f;
    private static final float MAX_LENGTH = 440f;
    private static final int SIDES = 36;
    private static final float SEGMENT = 12f;
    private static final float SKY_RADIUS = 90f;

    private static final float[][] LAYERS = {
            {56f, 5f, 120f, 0.6f, 0.003f, 1f / (30 * 20), 0.95f},
            {42f, 4f, 90f, 0.8f, -0.004f, 1f / (24 * 20), 0.8f},
            {31f, 3f, 70f, 1.0f, 0.006f, 1f / (18 * 20), 0.55f},
    };
    private static final float STAR_SPIN = 1f / (40 * 20);
    private static final float[][][] CLOUD_COLORS = {
            {{0.08f, 0.12f, 0.40f}, {0.22f, 0.14f, 0.55f}, {0.50f, 0.16f, 0.50f}, {0.10f, 0.32f, 0.50f}},
            {{0.10f, 0.10f, 0.38f}, {0.30f, 0.12f, 0.50f}, {0.60f, 0.20f, 0.40f}, {0.55f, 0.35f, 0.20f}},
            {{0.08f, 0.10f, 0.42f}, {0.26f, 0.12f, 0.58f}, {0.55f, 0.14f, 0.55f}, {0.12f, 0.30f, 0.55f}},
            {{0.10f, 0.08f, 0.36f}, {0.45f, 0.38f, 0.12f}, {0.55f, 0.50f, 0.60f}, {0.20f, 0.30f, 0.60f}},
    };
    private static final float[][][] TRAIL_COLORS = {
            {{1f, 1f, 1f}, {0.5f, 0.9f, 1f}, {0.3f, 0.4f, 1f}},
            {{1f, 0.9f, 0.4f}, {1f, 0.55f, 0.25f}, {1f, 0.35f, 0.5f}},
            {{1f, 0.85f, 0.45f}, {1f, 0.45f, 0.65f}, {0.6f, 0.35f, 1f}},
            {{1f, 1f, 0.85f}, {1f, 0.8f, 0.3f}, {0.5f, 0.7f, 1f}},
    };

    private static final int STARS = 1200;
    private static final int NEBULAE = 16;
    private static final Vector3f[] STAR_DIR = new Vector3f[STARS];
    private static final float[] STAR_SIZE = new float[STARS];
    private static final float[] STAR_PHASE = new float[STARS];
    private static final Vector3f[] NEBULA_DIR = new Vector3f[NEBULAE];
    private static final float[] NEBULA_SIZE = new float[NEBULAE];
    private static final float[] NEBULA_UV = new float[NEBULAE];
    private static final int[] NEBULA_TINT = new int[NEBULAE];

    private static final BlockState[] DEBRIS_BLOCKS = {
            Blocks.AMETHYST_BLOCK.defaultBlockState(),
            Blocks.BUDDING_AMETHYST.defaultBlockState(),
            Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
    };
    private static final float DEBRIS_SPACING = 16f;
    private static final float DEBRIS_RANGE = 240f;

    static {
        Random random = new Random(1791);
        for (int i = 0; i < STARS; i++) {
            STAR_DIR[i] = randomDirection(random);
            STAR_SIZE[i] = random.nextFloat() < 0.08f ? 1.2f : 0.4f + random.nextFloat() * 0.35f;
            STAR_PHASE[i] = random.nextFloat() * 100f;
        }
        for (int i = 0; i < NEBULAE; i++) {
            NEBULA_DIR[i] = randomDirection(random);
            NEBULA_SIZE[i] = 55f + random.nextFloat() * 70f;
            NEBULA_UV[i] = random.nextInt(4) * 0.25f;
            NEBULA_TINT[i] = random.nextInt(4);
        }
    }

    private static Vector3f randomDirection(Random random) {
        float z = random.nextFloat() * 2 - 1;
        float a = random.nextFloat() * Mth.TWO_PI;
        float r = Mth.sqrt(1 - z * z);
        return new Vector3f(r * Mth.cos(a), r * Mth.sin(a), z);
    }

    private static float length() {
        return Math.min(MAX_LENGTH, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8f);
    }

    private static Entity body() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player.getVehicle() != null ? player.getVehicle() : player;
    }

    private static Vec3 rideCenter(float partialTick) {
        Entity body = body();
        return body.getPosition(partialTick).add(0, body.getBbHeight() * 0.5, 0);
    }

    private static BufferBuilder begin(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static void draw(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static float fade(float z, float length) {
        return Mth.clamp(1 + z / 40f, 0f, 1f) * (1 - smoothstep(0.55f, 1f, z / length));
    }

    public static void renderSky(WorldRenderContext context) {
        if (Minecraft.getInstance().player == null) return;
        WarpTextures.ensureReady();

        float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
        float time = RideClient.time(pt);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        renderSpace(new Matrix4f(context.positionMatrix()).rotateZ(time * 0.0004f), time);
        Vec3 cam = context.camera().getPosition();
        Vec3 center = rideCenter(pt);
        renderVortex(new Matrix4f(context.positionMatrix()).translate((float) (center.x - cam.x), (float) (center.y - cam.y),
                (float) (center.z - cam.z)), time, pt);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void renderSpace(Matrix4f pose, float time) {
        float[][] tints = CLOUD_COLORS[RideClient.mount().ordinal()];
        additive();
        BufferBuilder nebula = begin(WarpTextures.NEBULA);
        for (int i = 0; i < NEBULAE; i++) {
            float[] t = tints[NEBULA_TINT[i]];
            float u = NEBULA_UV[i];
            billboard(nebula, pose, NEBULA_DIR[i], SKY_RADIUS * 1.1f, NEBULA_SIZE[i], u, 0, u + 0.75f, 0.75f,
                    t[0] * 1.6f, t[1] * 1.6f, t[2] * 1.6f, 0.6f);
        }
        draw(nebula);
        BufferBuilder stars = begin(WarpTextures.STAR);
        for (int i = 0; i < STARS; i++) {
            float twinkle = Mth.sin(time * 0.03f + STAR_PHASE[i]) > 0.7f ? 0.7f : 0.9f;
            billboard(stars, pose, STAR_DIR[i], SKY_RADIUS, STAR_SIZE[i], 0, 0, 1, 1, 0.85f, 0.9f, 1f, twinkle);
        }
        draw(stars);
    }

    private static void billboard(BufferBuilder bb, Matrix4f pose, Vector3f dir, float distance, float size,
                                  float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Vector3f up = Math.abs(dir.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(dir).cross(up).normalize().mul(size / 2);
        Vector3f upv = new Vector3f(right).cross(dir).normalize().mul(size / 2);
        float cx = dir.x * distance, cy = dir.y * distance, cz = dir.z * distance;
        bb.addVertex(pose, cx - right.x - upv.x, cy - right.y - upv.y, cz - right.z - upv.z).setUv(u0, v1).setColor(r, g, b, a);
        bb.addVertex(pose, cx + right.x - upv.x, cy + right.y - upv.y, cz + right.z - upv.z).setUv(u1, v1).setColor(r, g, b, a);
        bb.addVertex(pose, cx + right.x + upv.x, cy + right.y + upv.y, cz + right.z + upv.z).setUv(u1, v0).setColor(r, g, b, a);
        bb.addVertex(pose, cx - right.x + upv.x, cy - right.y + upv.y, cz - right.z + upv.z).setUv(u0, v0).setColor(r, g, b, a);
    }

    private static void renderVortex(Matrix4f pose, float time, float pt) {
        float progress = (float) RideClient.progress(pt);
        float offX = RideClient.offX(pt), offY = RideClient.offY(pt);
        float length = length();
        float[][] colors = CLOUD_COLORS[RideClient.mount().ordinal()];
        for (int l = 0; l < LAYERS.length; l++) {
            if (l == LAYERS.length - 1) {
                additive();
                BufferBuilder stars = begin(WarpTextures.STARFIELD);
                cylinder(stars, pose, 36f, 6f, 50f, 1.3f, 0.002f, STAR_SPIN, time, progress, offX, offY, length,
                        null, 0.95f);
                draw(stars);
            }
            RenderSystem.defaultBlendFunc();
            float[] layer = LAYERS[l];
            BufferBuilder clouds = begin(WarpTextures.CLOUD);
            cylinder(clouds, pose, layer[0], layer[1], layer[2], layer[3], layer[4], layer[5], time, progress,
                    offX, offY, length, colors, layer[6]);
            draw(clouds);
        }

        additive();
        float[] inner = LAYERS[LAYERS.length - 1];
        BufferBuilder glowWisps = begin(WarpTextures.CLOUD);
        cylinder(glowWisps, pose, inner[0] * 0.98f, inner[1], inner[2], inner[3], inner[4], inner[5], time,
                progress, offX, offY, length, colors, 0.3f);
        draw(glowWisps);

        BufferBuilder sparks = begin(WarpTextures.STAR);
        float stretch = RideClient.speed() / WarpTunnel.CRUISE_SPEED;
        for (RideClient.Streak s : RideClient.streaks()) {
            float z = Mth.lerp(pt, s.prevZ, s.z);
            if (z > length) continue;
            float base = s.brightness * fade(z, length);
            float cos = Mth.cos(s.angle) * s.radius, sin = Mth.sin(s.angle) * s.radius;
            for (int t = 0; t < 3; t++) {
                float tz = z + t * s.length * 0.2f * stretch;
                float x = WarpTunnel.bendX(tz, time) - offX + cos;
                float y = WarpTunnel.bendY(tz, time) - offY + sin;
                WormholeVisual.square(sparks, pose, x, y, tz, s.width * 2.4f * (1 - t * 0.25f), 0.85f, 0.9f, 1f,
                        base * (1 - t * 0.35f));
            }
        }
        draw(sparks);
    }

    private static void cylinder(BufferBuilder bb, Matrix4f pose, float radius, float uRepeat, float vScale,
                                 float scroll, float twist, float spin, float time, float progress, float offX,
                                 float offY, float length, float[][] colors, float alpha) {
        float[] c0 = new float[3], c1 = new float[3];
        float turn = (float) (time * (double) spin - Math.floor(time * (double) spin));
        float spun = turn * uRepeat;
        float turnAngle = turn * Mth.TWO_PI;
        float scrolled = Mth.floor(Mth.frac(progress * scroll / vScale) * 64f) / 64f;
        for (float z0 = Z_NEAR; z0 < length; z0 += SEGMENT) {
            float z1 = Math.min(length, z0 + SEGMENT);
            float a0 = alpha * fade(z0, length), a1 = alpha * fade(z1, length);
            if (a0 <= 0.001f && a1 <= 0.001f) continue;
            float cx0 = WarpTunnel.bendX(z0, time) - offX, cy0 = WarpTunnel.bendY(z0, time) - offY;
            float cx1 = WarpTunnel.bendX(z1, time) - offX, cy1 = WarpTunnel.bendY(z1, time) - offY;
            float v0 = z0 / vScale + scrolled, v1 = z1 / vScale + scrolled;
            float u0Base = spun + z0 * twist, u1Base = spun + z1 * twist;
            for (int s = 0; s < SIDES; s++) {
                float t0 = s * Mth.TWO_PI / SIDES, t1 = (s + 1) * Mth.TWO_PI / SIDES;
                float f0 = (float) s / SIDES, f1 = (float) (s + 1) / SIDES;
                cloudColor(colors, t0 + turnAngle, z0, time, c0);
                cloudColor(colors, t1 + turnAngle, z0, time, c1);
                bb.addVertex(pose, cx0 + Mth.cos(t0) * radius, cy0 + Mth.sin(t0) * radius, z0)
                        .setUv(f0 * uRepeat + u0Base, v0).setColor(c0[0], c0[1], c0[2], a0);
                bb.addVertex(pose, cx0 + Mth.cos(t1) * radius, cy0 + Mth.sin(t1) * radius, z0)
                        .setUv(f1 * uRepeat + u0Base, v0).setColor(c1[0], c1[1], c1[2], a0);
                cloudColor(colors, t1 + turnAngle, z1, time, c1);
                cloudColor(colors, t0 + turnAngle, z1, time, c0);
                bb.addVertex(pose, cx1 + Mth.cos(t1) * radius, cy1 + Mth.sin(t1) * radius, z1)
                        .setUv(f1 * uRepeat + u1Base, v1).setColor(c1[0], c1[1], c1[2], a1);
                bb.addVertex(pose, cx1 + Mth.cos(t0) * radius, cy1 + Mth.sin(t0) * radius, z1)
                        .setUv(f0 * uRepeat + u1Base, v1).setColor(c0[0], c0[1], c0[2], a1);
            }
        }
    }

    private static void cloudColor(float[][] colors, float theta, float z, float time, float[] out) {
        if (colors == null) {
            out[0] = out[1] = out[2] = 1f;
            return;
        }
        palette(colors, 0.5f + 0.5f * Mth.sin(theta * 2 + z * 0.012f + time * 0.01f), out);
    }

    public static void renderWorld(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!RideClient.isActive() || mc.player == null
                || mc.player.level().dimension() != Wormholes.ULTRA_WARP) return;
        WarpTextures.ensureReady();

        float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
        float time = RideClient.time(pt);
        double progress = RideClient.progress(pt);
        float offX = RideClient.offX(pt);
        float offY = RideClient.offY(pt);
        float length = length();
        Quaternionf cameraRotation = context.camera().rotation();
        Vec3 cam = context.camera().getPosition();
        Vec3 center = rideCenter(pt);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
        Vector3f toCamera = new Vector3f((float) (cam.x - center.x), (float) (cam.y - center.y), (float) (cam.z - center.z));

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        renderDebris(mc, buffers, poseStack, time, progress, offX, offY);
        buffers.endBatch(Sheets.cutoutBlockSheet());
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        buffers.endBatch(Sheets.translucentItemSheet());
        renderWormholes(buffers, poseStack, cameraRotation, time, progress, offX, offY, length, pt);
        renderGoldRings(buffers, poseStack, time, progress, offX, offY, length, pt);
        renderTrail(buffers, poseStack.last().pose(), toCamera, progress, offX, offY);

        for (ResourceLocation texture : new ResourceLocation[]{WarpTextures.GLOW, WarpTextures.FUNNEL,
                WarpTextures.STAR, WarpTextures.RING, WarpTextures.RING_GLOW}) {
            buffers.endBatch(WarpRenderTypes.additive(texture));
        }
        buffers.endBatch();
    }

    private static void renderWormholes(MultiBufferSource buffers, PoseStack poseStack, Quaternionf cameraRotation,
                                        float time, double progress, float offX, float offY, float length, float pt) {
        TunnelLayout layout = RideClient.layout();
        for (int i = layout.firstIndexAtOrAfter(progress - 60); ; i++) {
            WarpTunnel.Wormhole w = layout.wormhole(i);
            float z = (float) (w.distance() - progress);
            if (z > length) break;
            if (z < -60) continue;

            float scale = 1f, alpha = 1f;
            int age = RideClient.touchedAge(i);
            if (age >= 0) {
                float f = (age + pt) / 14f;
                if (f >= 1) continue;
                scale = 1 + f * 2.5f;
                alpha = 1 - f;
            }
            alpha *= 1 - smoothstep(0.65f, 1f, z / length);
            alpha *= Mth.clamp(1 + z / 40f, 0f, 1f);
            if (alpha <= 0.01f) continue;

            poseStack.pushPose();
            poseStack.translate(w.x() - offX + WarpTunnel.bendX(z, time), w.y() - offY + WarpTunnel.bendY(z, time), z);
            WormholeVisual.draw(buffers, poseStack.last().pose(), facing(z, time), cameraRotation, time + i * 37f,
                    w.radius() * scale, w.rings(), w.type(), w.rainbow(), alpha);
            String name = layout.name(i);
            if (name != null && age < 0) drawName(buffers, poseStack, cameraRotation, name, w.radius() * 2.4f, alpha);
            poseStack.popPose();
        }
    }

    private static Quaternionf facing(float z, float time) {
        float slopeX = (WarpTunnel.bendX(z + 1, time) - WarpTunnel.bendX(z - 1, time)) / 2;
        float slopeY = (WarpTunnel.bendY(z + 1, time) - WarpTunnel.bendY(z - 1, time)) / 2;
        return new Quaternionf().rotationTo(0, 0, 1, -slopeX, -slopeY, -1);
    }

    private static void drawName(MultiBufferSource buffers, PoseStack poseStack, Quaternionf cameraRotation,
                                 String name, float above, float alpha) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0, above, 0);
        poseStack.mulPose(cameraRotation);
        poseStack.scale(0.15f, -0.15f, 0.15f);
        int a = Mth.clamp((int) (alpha * 255), 8, 255);
        font.drawInBatch(name, -font.width(name) / 2f, 0, (a << 24) | 0xFFE9A8, true, poseStack.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void renderGoldRings(MultiBufferSource buffers, PoseStack poseStack, float time, double progress,
                                        float offX, float offY, float length, float pt) {
        long seed = RideClient.seed();
        for (int i = WarpTunnel.firstGoldSlotAtOrAfter(progress - 60); ; i++) {
            WarpTunnel.GoldRing ring = WarpTunnel.goldRing(seed, i);
            double at = WarpTunnel.goldRingPassedAt(seed, i);
            float z = (float) (at - progress);
            if (z > length) break;
            if (ring == null || z < -30) continue;

            float scale = 1f, alpha = 1f;
            int age = RideClient.collectedAge(i);
            if (age >= 0) {
                float f = (age + pt) / 12f;
                if (f >= 1) continue;
                scale = 1 + f * 1.8f;
                alpha = 1 - f;
            }
            alpha *= 1 - smoothstep(0.55f, 0.9f, z / length);
            alpha *= Mth.clamp(1 + z / 20f, 0f, 1f);
            if (alpha <= 0.01f) continue;
            float pulse = 1f + 0.06f * Mth.sin(Mth.floor(time / 3) * 0.6f + i);
            poseStack.pushPose();
            poseStack.translate(ring.x() - offX + WarpTunnel.bendX(z, time), ring.y() - offY + WarpTunnel.bendY(z, time), z);
            poseStack.mulPose(facing(z, time));
            WormholeVisual.goldRing(buffers, poseStack.last().pose(), WarpTunnel.GoldRing.RADIUS * scale * pulse, alpha);
            poseStack.popPose();
        }
    }

    private static void renderDebris(Minecraft mc, MultiBufferSource buffers, PoseStack poseStack, float time,
                                     double progress, float offX, float offY) {
        long seed = RideClient.seed() ^ 0x5DEECE66DL;
        int first = Math.max(0, Mth.floor((progress + Z_NEAR) / DEBRIS_SPACING) - 1);
        int last = Mth.floor((progress + DEBRIS_RANGE) / DEBRIS_SPACING) + 1;
        for (int i = first; i <= last; i++) {
            Random random = new Random(seed + i * 0x9E3779B97F4A7C15L);
            float z = (float) (i * DEBRIS_SPACING + random.nextFloat() * DEBRIS_SPACING - progress);
            if (z < Z_NEAR || z > DEBRIS_RANGE) continue;
            float angle = random.nextFloat() * Mth.TWO_PI;
            float r = 26f + random.nextFloat() * 10f;
            float size = 1.2f + random.nextFloat() * 1.6f;
            BlockState block = DEBRIS_BLOCKS[random.nextInt(DEBRIS_BLOCKS.length)];
            int count = 1 + random.nextInt(4);
            Vector3f axis = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f)
                    .normalize();
            float spinSpeed = 0.01f + random.nextFloat() * 0.03f;

            poseStack.pushPose();
            poseStack.translate(WarpTunnel.bendX(z, time) - offX + Mth.cos(angle) * r,
                    WarpTunnel.bendY(z, time) - offY + Mth.sin(angle) * r, z);
            poseStack.mulPose(Axis.of(axis).rotation(time * spinSpeed + i));
            poseStack.scale(size, size, size);
            for (int b = 0; b < count; b++) {
                poseStack.pushPose();
                BlockState state = block;
                if (b == 0) {
                    poseStack.translate(-0.5f, -0.5f, -0.5f);
                } else {
                    poseStack.translate(random.nextInt(3) - 1.5f, random.nextInt(3) - 1.5f, random.nextInt(3) - 1.5f);
                    state = DEBRIS_BLOCKS[random.nextInt(DEBRIS_BLOCKS.length)];
                }
                mc.getBlockRenderer().renderSingleBlock(state, poseStack, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    private static void renderTrail(MultiBufferSource buffers, Matrix4f pose, Vector3f toCamera,
                                    double progress, float offX, float offY) {
        float[][] colors = TRAIL_COLORS[RideClient.mount().ordinal()];
        float drop = -body().getBbHeight() * 0.2f;
        int n = RideClient.TRAIL_LENGTH;
        Vector3f[] points = new Vector3f[n];
        points[0] = new Vector3f(0, drop, 0);
        for (int k = 1; k < n; k++) {
            points[k] = new Vector3f(RideClient.trailX(k) - offX, RideClient.trailY(k) - offY + drop,
                    (float) (RideClient.trailProgress(k) - progress));
        }

        VertexConsumer vc = buffers.getBuffer(WarpRenderTypes.additive(WarpTextures.GLOW));
        float[] c0 = new float[3], c1 = new float[3];
        Vector3f side0 = new Vector3f(), side1 = new Vector3f();
        for (int k = 0; k < n - 1; k++) {
            float f0 = (float) k / (n - 1), f1 = (float) (k + 1) / (n - 1);
            trailSide(points, k, toCamera, (1.4f - f0) * 1.3f, side0);
            trailSide(points, k + 1, toCamera, (1.4f - f1) * 1.3f, side1);
            palette(colors, f0, c0);
            palette(colors, f1, c1);
            float a0 = 0.9f * (1 - f0), a1 = 0.9f * (1 - f1);
            Vector3f p0 = points[k], p1 = points[k + 1];
            vc.addVertex(pose, p0.x - side0.x, p0.y - side0.y, p0.z - side0.z).setUv(0, 0.5f).setColor(c0[0], c0[1], c0[2], a0);
            vc.addVertex(pose, p0.x + side0.x, p0.y + side0.y, p0.z + side0.z).setUv(1, 0.5f).setColor(c0[0], c0[1], c0[2], a0);
            vc.addVertex(pose, p1.x + side1.x, p1.y + side1.y, p1.z + side1.z).setUv(1, 0.5f).setColor(c1[0], c1[1], c1[2], a1);
            vc.addVertex(pose, p1.x - side1.x, p1.y - side1.y, p1.z - side1.z).setUv(0, 0.5f).setColor(c1[0], c1[1], c1[2], a1);
        }
    }

    private static void trailSide(Vector3f[] points, int k, Vector3f toCamera, float halfWidth, Vector3f out) {
        Vector3f a = points[Math.max(0, k - 1)], b = points[Math.min(points.length - 1, k + 1)];
        Vector3f dir = new Vector3f(b).sub(a);
        if (dir.lengthSquared() < 1e-6f) dir.set(0, 0, -1);
        Vector3f view = new Vector3f(toCamera).sub(points[k]);
        dir.cross(view, out);
        if (out.lengthSquared() < 1e-6f) out.set(1, 0, 0);
        out.normalize().mul(halfWidth);
    }

    private static void palette(float[][] colors, float f, float[] out) {
        float t = f * (colors.length - 1);
        int i = Math.min((int) t, colors.length - 2);
        float g = t - i;
        for (int k = 0; k < 3; k++) out[k] = Mth.lerp(g, colors[i][k], colors[i + 1][k]);
    }
}

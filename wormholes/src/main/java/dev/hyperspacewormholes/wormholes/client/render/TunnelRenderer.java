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
import dev.hyperspacewormholes.wormholes.client.RideCamera;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.RideOpening;
import dev.hyperspacewormholes.wormholes.client.ShaderPacks;
import dev.hyperspacewormholes.wormholes.client.WarpEntryCamera;
import dev.hyperspacewormholes.wormholes.client.WarpEntryClose;
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
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Draws Ultra Space during the ride, pixel-art style: the tunnel (UltraTunnel), the opening's corridor
 * (RideOpeningRenderer), wormholes, golden rings and blocks drifting along it, and the light trail behind you as you steer.
 */
public final class TunnelRenderer {
    private static final float Z_NEAR = -50f;
    private static final float MAX_LENGTH = 440f;
    private static float heldBack;
    private static final float[][][] TRAIL_COLORS = {
            {{1f, 1f, 1f}, {0.5f, 0.9f, 1f}, {0.3f, 0.4f, 1f}},
            {{1f, 0.9f, 0.4f}, {1f, 0.55f, 0.25f}, {1f, 0.35f, 0.5f}},
            {{1f, 0.85f, 0.45f}, {1f, 0.45f, 0.65f}, {0.6f, 0.35f, 1f}},
            {{1f, 1f, 0.85f}, {1f, 0.8f, 0.3f}, {0.5f, 0.7f, 1f}},
    };

    private static final BlockState[] DEBRIS_BLOCKS = {
            Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
    };
    private static final float DEBRIS_SPACING = 16f;
    private static final float DEBRIS_RANGE = 240f;
    private static final Vector3f SCRATCH_A = new Vector3f(), SCRATCH_B = new Vector3f();

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

    private static final RideOpeningRenderer.Batch OPENING = new RideOpeningRenderer.Batch() {
        @Override
        public VertexConsumer begin(int texture, boolean additive) {
            if (additive) additive();
            else RenderSystem.defaultBlendFunc();
            return TunnelRenderer.begin(switch (texture) {
                case RideOpeningRenderer.WALL -> WarpTextures.WALL;
                case RideOpeningRenderer.RUSH_WALL -> WarpTextures.RUSH_WALL;
                case RideOpeningRenderer.BANDS -> WarpTextures.BANDS;
                case RideOpeningRenderer.CURTAINS -> WarpTextures.CURTAINS;
                case RideOpeningRenderer.SPARKLES -> WarpTextures.SPARKLES;
                case RideOpeningRenderer.SMOKE -> WarpTextures.SMOKE;
                default -> WarpTextures.STAR;
            });
        }

        @Override
        public void end(VertexConsumer consumer) {
            draw((BufferBuilder) consumer);
        }
    };

    private static final UltraTunnel.Batch TUNNEL = new UltraTunnel.Batch() {
        @Override
        public VertexConsumer begin(ResourceLocation texture, boolean additive) {
            if (additive) additive();
            else RenderSystem.defaultBlendFunc();
            return TunnelRenderer.begin(texture);
        }

        @Override
        public void end(VertexConsumer consumer) {
            draw((BufferBuilder) consumer);
        }
    };

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

    public static void renderSky(WorldRenderContext context) {
        if (Minecraft.getInstance().player == null || ShaderPacks.inUse()) return;
        drawSky(context);
    }

    /**
     * With a shader pack on, the sky is drawn here, after the pack's deferred pass, instead of as the sky: a pack like BSL
     * adds its own sky colour to every pixel nothing solid covers in that pass, which turned the ride's purple a pale teal
     * by day. Drawn at the very back (depth range 1) so the ride and everything else stay in front, then the tunnel on
     * top, as without a pack (renderWorld skips itself then).
     */
    public static void renderAfterWater(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!ShaderPacks.inUse() || mc.player == null || mc.player.level().dimension() != Wormholes.ULTRA_WARP) return;
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        GL11.glDepthRange(1, 1);
        try {
            drawSky(context);
        } finally {
            GL11.glDepthRange(0, 1);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        drawWorld(context);
    }

    private static void drawSky(WorldRenderContext context) {
        boolean late = ShaderPacks.inUse();
        WarpTextures.ensureReady();
        UltraTunnelTextures.ensureReady();

        float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
        heldBack = WarpEntryCamera.heldBack(pt);
        float time = RideClient.time(pt);
        RenderSystem.disableCull();
        if (late) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        Vec3 cam = context.camera().getPosition();
        Vec3 center = rideCenter(pt);
        float shrink = Math.min(1f, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8f / (MAX_LENGTH + (float) center.distanceTo(cam)));
        Matrix4f atRide = new Matrix4f(context.positionMatrix()).scale(shrink).translate((float) (center.x - cam.x), (float) (center.y - cam.y),
                (float) (center.z - cam.z));
        boolean opening = RideOpening.playing(pt);
        if (!opening || RideOpening.frame(pt) >= RideOpening.HOLE_OPENS) {
            UltraTunnel.draw(TUNNEL, atRide, time, pt, MAX_LENGTH, heldBack);
        }
        if (opening) {
            RideOpeningRenderer.render(OPENING, context.positionMatrix(), (float) (cam.x - center.x), (float) (cam.y - center.y),
                    (float) (cam.z - center.z), RideCamera.unit(body()),
                    Minecraft.getInstance().gameRenderer.getDepthFar(), RideOpening.frame(pt),
                    UltraTunnel.CLOUDS);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    public static void renderWorld(WorldRenderContext context) {
        if (!ShaderPacks.inUse()) drawWorld(context);
    }

    private static void drawWorld(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!RideClient.isActive() || mc.player == null || mc.player.level().dimension() != Wormholes.ULTRA_WARP) return;
        WarpTextures.ensureReady();
        WarpCloseTextures.ensureReady();

        float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
        heldBack = WarpEntryCamera.heldBack(pt);
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
        renderDebris(mc, buffers, poseStack, toCamera, time, progress, offX, offY, pt);
        buffers.endBatch(Sheets.cutoutBlockSheet());
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        buffers.endBatch(Sheets.translucentItemSheet());
        float arrival = RideClient.arrival(pt);
        if (arrival <= 0f) return;
        renderWormholes(buffers, poseStack, cameraRotation, time, progress, offX, offY, length, pt, arrival,
                context.projectionMatrix().m11());
        renderGoldRings(buffers, poseStack, time, progress, offX, offY, length, pt, arrival);
        renderTrail(buffers, poseStack.last().pose(), toCamera, progress, offX, offY, arrival * WarpEntryClose.trail(pt));

        for (ResourceLocation texture : new ResourceLocation[]{WarpTextures.GLOW, WarpTextures.FUNNEL,
                WarpTextures.STAR, WarpTextures.RING, WarpTextures.RING_GLOW}) {
            buffers.endBatch(WarpRenderTypes.additive(texture));
        }
        buffers.endBatch();
    }

    private static void renderWormholes(MultiBufferSource buffers, PoseStack poseStack, Quaternionf cameraRotation,
                                        float time, double progress, float offX, float offY, float length, float pt, float arrival, float zoom) {
        TunnelLayout layout = RideClient.layout();
        Matrix4f light = null;
        for (int i = layout.firstIndexAtOrAfter(progress - 60); ; i++) {
            WarpTunnel.Wormhole w = layout.wormhole(i);
            float z = (float) (w.distance() - progress);
            if (z > length) break;
            if (z < -60 - heldBack) continue;

            float scale = 1f, alpha = 1f;
            int age = RideClient.touchedAge(i);
            boolean closing = WarpEntryClose.of(i);
            if (closing) {
                scale = WarpEntryClose.holeScale(pt);
                alpha = WarpEntryClose.holeLight(pt);
            } else if (age >= 0) {
                continue;
            }
            alpha *= arrival * (1 - smoothstep(0.65f, 1f, z / length));
            alpha *= Mth.clamp(1 + (z + heldBack) / 40f, 0f, 1f);
            if (WarpEntryClose.before(w.distance())) alpha *= WarpEntryClose.trail(pt);
            if (alpha <= 0.01f && !closing) continue;

            poseStack.pushPose();
            poseStack.translate(w.x() - offX + WarpTunnel.bendX(z, time), w.y() - offY + WarpTunnel.bendY(z, time), z);
            if (alpha > 0.002f) {
                WormholeVisual.draw(buffers, poseStack.last().pose(), facing(z, time), cameraRotation, time + i * 37f,
                        w.radius() * scale, w.rings(), w.type(), w.rainbow(), alpha);
            }
            String name = layout.name(i);
            float named = alpha * (closing ? WarpEntryClose.trail(pt) : 1f);
            if (name != null && named > 0.04f) drawName(buffers, poseStack, cameraRotation, name, w.radius() * 2.4f, named);
            if (closing) light = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
        }
        if (light != null) {
            WarpCloseLook.draw(buffers.getBuffer(WarpRenderTypes.additive(WarpCloseTextures.SHEET)), light, cameraRotation,
                    zoom, WarpEntryClose.frame(pt), WarpEntryClose.strength(pt), WarpEntryClose.sun());
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
                                        float offX, float offY, float length, float pt, float arrival) {
        long seed = RideClient.seed();
        for (int i = WarpTunnel.firstGoldSlotAtOrAfter(progress - 60); ; i++) {
            WarpTunnel.GoldRing ring = WarpTunnel.goldRing(seed, i);
            double at = WarpTunnel.goldRingPassedAt(seed, i);
            float z = (float) (at - progress);
            if (z > length) break;
            if (ring == null || z < -30 - heldBack) continue;

            float scale = 1f, alpha = 1f;
            int age = RideClient.collectedAge(i);
            if (age >= 0) {
                float f = (age + pt) / 12f;
                if (f >= 1) continue;
                scale = 1 + f * 1.8f;
                alpha = 1 - f;
            }
            alpha *= arrival * (1 - smoothstep(0.55f, 0.9f, z / length));
            alpha *= Mth.clamp(1 + (z + heldBack) / 20f, 0f, 1f);
            if (WarpEntryClose.before(at)) alpha *= WarpEntryClose.trail(pt);
            if (alpha <= 0.01f) continue;
            float pulse = 1f + 0.06f * Mth.sin(Mth.floor(time / 3) * 0.6f + i);
            poseStack.pushPose();
            poseStack.translate(ring.x() - offX + WarpTunnel.bendX(z, time), ring.y() - offY + WarpTunnel.bendY(z, time), z);
            poseStack.mulPose(facing(z, time));
            WormholeVisual.goldRing(buffers, poseStack.last().pose(), WarpTunnel.GoldRing.RADIUS * scale * pulse, alpha);
            poseStack.popPose();
        }
    }

    /** How much of something in the tunnel shows through the mouth of the opening's corridor, 0 to 1. */
    private static float throughMouth(Vector3f toCamera, float x, float y, float z, float mouth, float rim) {
        if (toCamera.z >= mouth) return 1f;
        if (z <= mouth) return 0f;
        float at = (mouth - toCamera.z) / (z - toCamera.z);
        float across = Mth.sqrt(Mth.square(Mth.lerp(at, toCamera.x, x)) + Mth.square(Mth.lerp(at, toCamera.y, y)));
        return Mth.clamp((0.9f - across / rim) / 0.25f, 0f, 1f);
    }

    private static void renderDebris(Minecraft mc, MultiBufferSource buffers, PoseStack poseStack, Vector3f toCamera,
                                     float time, double progress, float offX, float offY, float pt) {
        boolean opening = RideOpening.playing(pt);
        float frame = RideOpening.frame(pt), unit = RideCamera.unit(body());
        float mouth = RideOpening.mouth(frame) * unit, rim = RideOpening.MOUTH_RADIUS * RideOpening.scale(frame) * unit;
        if (opening && mouth > DEBRIS_RANGE) return;
        long seed = RideClient.seed() ^ 0x5DEECE66DL;
        int first = Mth.floor((progress + Z_NEAR - heldBack) / DEBRIS_SPACING) - 1;
        int last = Mth.floor((progress + DEBRIS_RANGE) / DEBRIS_SPACING) + 1;
        for (int i = first; i <= last; i++) {
            Random random = new Random(seed + i * 0x9E3779B97F4A7C15L);
            float z = (float) (i * DEBRIS_SPACING + random.nextFloat() * DEBRIS_SPACING - progress);
            if (z < Z_NEAR - heldBack || z > DEBRIS_RANGE) continue;
            float angle = random.nextFloat() * Mth.TWO_PI;
            float r = 26f + random.nextFloat() * 10f;
            float size = 1.2f + random.nextFloat() * 1.6f;
            BlockState block = DEBRIS_BLOCKS[random.nextInt(DEBRIS_BLOCKS.length)];
            int count = 1 + random.nextInt(4);
            Vector3f axis = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f)
                    .normalize();
            float spinSpeed = 0.01f + random.nextFloat() * 0.03f;

            float x = WarpTunnel.bendX(z, time) - offX + Mth.cos(angle) * r;
            float y = WarpTunnel.bendY(z, time) - offY + Mth.sin(angle) * r;
            if (opening) size *= throughMouth(toCamera, x, y, z, mouth, rim);
            if (size < 0.05f) continue;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
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
                                    double progress, float offX, float offY, float arrival) {
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
            palette(colors, f0, c0, 0);
            palette(colors, f1, c1, 0);
            Vector3f p0 = points[k], p1 = points[k + 1];
            float a0 = 0.9f * arrival * (1 - f0), a1 = 0.9f * arrival * (1 - f1);
            vc.addVertex(pose, p0.x - side0.x, p0.y - side0.y, p0.z - side0.z).setUv(0, 0.5f).setColor(c0[0], c0[1], c0[2], a0);
            vc.addVertex(pose, p0.x + side0.x, p0.y + side0.y, p0.z + side0.z).setUv(1, 0.5f).setColor(c0[0], c0[1], c0[2], a0);
            vc.addVertex(pose, p1.x + side1.x, p1.y + side1.y, p1.z + side1.z).setUv(1, 0.5f).setColor(c1[0], c1[1], c1[2], a1);
            vc.addVertex(pose, p1.x - side1.x, p1.y - side1.y, p1.z - side1.z).setUv(0, 0.5f).setColor(c1[0], c1[1], c1[2], a1);
        }
    }

    private static void trailSide(Vector3f[] points, int k, Vector3f toCamera, float halfWidth, Vector3f out) {
        Vector3f a = points[Math.max(0, k - 1)], b = points[Math.min(points.length - 1, k + 1)];
        Vector3f dir = SCRATCH_A.set(b).sub(a);
        if (dir.lengthSquared() < 1e-6f) dir.set(0, 0, -1);
        Vector3f view = SCRATCH_B.set(toCamera).sub(points[k]);
        dir.cross(view, out);
        if (out.lengthSquared() < 1e-6f) out.set(1, 0, 0);
        out.normalize().mul(halfWidth);
    }

    private static void palette(float[][] colors, float f, float[] out, int at) {
        float t = f * (colors.length - 1);
        int i = Math.min((int) t, colors.length - 2);
        float g = t - i;
        for (int k = 0; k < 3; k++) out[at + k] = Mth.lerp(g, colors[i][k], colors[i + 1][k]);
    }
}

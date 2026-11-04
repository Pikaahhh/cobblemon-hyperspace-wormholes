package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Banks of vanilla clouds in stacked layers THICK tall (vanilla's are 4) with no gaps, each layer showing a different part of the cloud texture so its neighbours fill its gaps, closed off by a solid sheet on the far side.
 * Layers are drawn farthest first in vanilla's two passes (depth, then colour), every face sits 0.2 or 0.7 of a block off the block grid so none lies on a block face, and vanilla's own clouds are off in both worlds (WormholesClient).
 * CLIFF is the sea of clouds under the Wilds Cliff World (five still layers below the land); PLANT is the Ultra Plant's storm deck, five layers drifting DRIFT blocks a tick under one solid sheet in the game's storm colour, lit by lightning flashes.
 */
public final class SkyClouds {
    public static final float FOG_START = 80, FOG_END = 260;
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static final float CELL = 12, THICK = 1.5f, TEXEL = 1 / 256f, EDGE = 1 / 1024f;
    private static final double[][] SHIFTS = {{0.3, 0.3}, {1003.3, 497.3}, {351.3, 1879.3}, {2291.3, 1163.3}, {1571.3, 2531.3}};
    private static final float SOLID_U = 59.5f / 256, SOLID_V = 87.5f / 256;
    private static final int SOLID_REACH = 32;
    private static final float FLASH_MIX = 0.45f;
    private static final float[] FLASH = rgb(0x8FB0D8);
    private static final float DRIFT = 0.05f;
    private static final float[] STORM = shade(rgb(0x152037), 0.7f);
    private static final Deck CLIFF = new Deck(UltraZones::isSky, new float[]{59.2f, 57.7f, 56.2f, 54.7f, 53.2f}, 53.2f,
            null, null, 0.75f, false, 0);
    private static final Deck PLANT = new Deck(level -> level.dimension() == Wormholes.ULTRA_PLANT,
            new float[]{168.2f, 169.7f, 171.2f, 172.7f, 174.2f}, 177.2f, STORM, STORM, 0.8f, true, DRIFT);
    private static final List<Deck> DECKS = List.of(CLIFF, PLANT);

    private static final class Deck {
        final Predicate<Level> in;
        final float[] bases;
        final float solidTop, alpha, drift;
        final float[] layerColour, solidColour;
        final boolean flashes;
        final Layer[] layers;
        VertexBuffer solid;
        Vec3 solidDrawn = Vec3.ZERO;

        Deck(Predicate<Level> in, float[] bases, float solidTop, float[] layerColour, float[] solidColour, float alpha,
             boolean flashes, float drift) {
            this.in = in;
            this.bases = bases;
            this.solidTop = solidTop;
            this.layerColour = layerColour;
            this.solidColour = solidColour;
            this.alpha = alpha;
            this.flashes = flashes;
            this.drift = drift;
            this.layers = new Layer[bases.length];
        }
    }

    private static final class Layer {
        VertexBuffer mesh;
        int cellX = Integer.MIN_VALUE, cellY, cellZ;
        Vec3 colour = Vec3.ZERO;
        float fx, fy, fz;
    }

    private SkyClouds() {}

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Deck deck = deckFor(mc.level);
        if (deck == null) return;
        Vec3 cam = context.camera().getPosition();
        float flash = deck.flashes && mc.level.getSkyFlashTime() > 0 ? FLASH_MIX : 0;
        Vec3 colour = colour(deck.layerColour, mc.level, context, flash);
        double drift = (mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false)) * deck.drift;
        Vec3 solidColour = deck.solidColour == null ? colour : colour(deck.solidColour, mc.level, context, flash);
        Integer[] order = new Integer[deck.bases.length];
        for (int n = 0; n < order.length; n++) order[n] = n;
        Arrays.sort(order, Comparator.comparingDouble(n -> -Math.abs(deck.bases[n] + THICK / 2 - cam.y)));
        FogRenderer.levelFogColor();
        RenderSystem.setShader(GameRenderer::getRendertypeCloudsShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        ShaderInstance shader = RenderSystem.getShader();
        drawSolid(deck, cam, solidColour, shader);
        for (int n : order) {
            Layer layer = update(deck, n, cam, drift, colour);
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).scale(CELL, 1, CELL).translate(-layer.fx, layer.fy, -layer.fz);
            layer.mesh.bind();
            RenderSystem.colorMask(false, false, false, false);
            layer.mesh.drawWithShader(view, RenderSystem.getProjectionMatrix(), shader);
            RenderSystem.colorMask(true, true, true, true);
            layer.mesh.drawWithShader(view, RenderSystem.getProjectionMatrix(), shader);
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static @Nullable Deck deckFor(Level level) {
        for (Deck deck : DECKS) if (deck.in.test(level)) return deck;
        return null;
    }

    private static Vec3 colour(float[] fixed, ClientLevel level, WorldRenderContext context, float flash) {
        Vec3 c = fixed == null ? level.getCloudColor(context.tickCounter().getGameTimeDeltaPartialTick(false))
                : new Vec3(fixed[0], fixed[1], fixed[2]);
        return new Vec3(Mth.lerp(flash, c.x, FLASH[0]), Mth.lerp(flash, c.y, FLASH[1]), Mth.lerp(flash, c.z, FLASH[2]));
    }

    private static void drawSolid(Deck deck, Vec3 cam, Vec3 colour, ShaderInstance shader) {
        if (deck.solid == null || deck.solidDrawn.distanceToSqr(colour) > 2.0E-4) {
            if (deck.solid == null) deck.solid = new VertexBuffer(VertexBuffer.Usage.STATIC);
            float r = (float) colour.x, g = (float) colour.y, b = (float) colour.z;
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (int x = -SOLID_REACH; x < SOLID_REACH; x++) {
                for (int z = -SOLID_REACH; z < SOLID_REACH; z++) {
                    tile(buffer, x, 0, z, r, g, b, 1);
                    tile(buffer, x, -THICK, z, r * 0.7f, g * 0.7f, b * 0.7f, -1);
                }
            }
            deck.solid.bind();
            deck.solid.upload(buffer.buildOrThrow());
            deck.solidDrawn = colour;
        }
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).scale(CELL, 1, CELL).translate(0, (float) (deck.solidTop - cam.y), 0);
        deck.solid.bind();
        deck.solid.drawWithShader(view, RenderSystem.getProjectionMatrix(), shader);
    }

    private static void tile(BufferBuilder buffer, float x, float y, float z, float r, float g, float b, float ny) {
        buffer.addVertex(x, y, z + 1).setUv(SOLID_U, SOLID_V).setColor(r, g, b, 1f).setNormal(0, ny, 0);
        buffer.addVertex(x + 1, y, z + 1).setUv(SOLID_U, SOLID_V).setColor(r, g, b, 1f).setNormal(0, ny, 0);
        buffer.addVertex(x + 1, y, z).setUv(SOLID_U, SOLID_V).setColor(r, g, b, 1f).setNormal(0, ny, 0);
        buffer.addVertex(x, y, z).setUv(SOLID_U, SOLID_V).setColor(r, g, b, 1f).setNormal(0, ny, 0);
    }

    private static Layer update(Deck deck, int n, Vec3 cam, double drift, Vec3 colour) {
        Layer layer = deck.layers[n] == null ? deck.layers[n] = new Layer() : deck.layers[n];
        double i = (cam.x + drift + SHIFTS[n][0]) / CELL, k = (cam.z + SHIFTS[n][1]) / CELL, j = deck.bases[n] - cam.y;
        i -= Mth.floor(i / 2048) * 2048;
        k -= Mth.floor(k / 2048) * 2048;
        int cellX = Mth.floor(i), cellY = Mth.floor(j / THICK), cellZ = Mth.floor(k);
        layer.fx = (float) (i - cellX);
        layer.fy = (float) (j - cellY * THICK);
        layer.fz = (float) (k - cellZ);
        if (layer.mesh == null || cellX != layer.cellX || cellY != layer.cellY || cellZ != layer.cellZ
                || layer.colour.distanceToSqr(colour) > 2.0E-4) {
            if (layer.mesh == null) layer.mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
            layer.mesh.bind();
            layer.mesh.upload(build(cellX * TEXEL, cellY * THICK, cellZ * TEXEL, colour, deck.alpha));
            VertexBuffer.unbind();
            layer.cellX = cellX;
            layer.cellY = cellY;
            layer.cellZ = cellZ;
            layer.colour = colour;
        }
        return layer;
    }

    private static MeshData build(float u0, float y, float v0, Vec3 colour, float alpha) {
        float r = (float) colour.x, g = (float) colour.y, b = (float) colour.z;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (int cx = -3; cx <= 4; cx++) {
            for (int cz = -3; cz <= 4; cz++) {
                float x = cx * 8, z = cz * 8;
                if (y > -5) {
                    face(buffer, u0, v0, r * 0.7f, g * 0.7f, b * 0.7f, alpha, 0, -1, 0,
                            x, y, z + 8, x + 8, y, z + 8, x + 8, y, z, x, y, z, 0, 0);
                }
                if (y <= 5) {
                    float top = y + THICK - EDGE;
                    face(buffer, u0, v0, r, g, b, alpha, 0, 1, 0,
                            x, top, z + 8, x + 8, top, z + 8, x + 8, top, z, x, top, z, 0, 0);
                }
                for (int s = 0; s < 8; s++) {
                    if (cx > -1) {
                        float at = x + s;
                        face(buffer, u0, v0, r * 0.9f, g * 0.9f, b * 0.9f, alpha, -1, 0, 0,
                                at, y, z + 8, at, y + THICK, z + 8, at, y + THICK, z, at, y, z, s + 0.5f - (at - x), 0);
                    }
                    if (cx <= 1) {
                        float at = x + s + 1 - EDGE;
                        face(buffer, u0, v0, r * 0.9f, g * 0.9f, b * 0.9f, alpha, 1, 0, 0,
                                at, y, z + 8, at, y + THICK, z + 8, at, y + THICK, z, at, y, z, s + 0.5f - (at - x), 0);
                    }
                    if (cz > -1) {
                        float at = z + s;
                        face(buffer, u0, v0, r * 0.8f, g * 0.8f, b * 0.8f, alpha, 0, 0, -1,
                                x, y + THICK, at, x + 8, y + THICK, at, x + 8, y, at, x, y, at, 0, s + 0.5f - (at - z));
                    }
                    if (cz <= 1) {
                        float at = z + s + 1 - EDGE;
                        face(buffer, u0, v0, r * 0.8f, g * 0.8f, b * 0.8f, alpha, 0, 0, 1,
                                x + 8, y + THICK, at, x, y + THICK, at, x, y, at, x + 8, y, at, 0, s + 0.5f - (at - z));
                    }
                }
            }
        }
        return buffer.buildOrThrow();
    }

    private static void face(BufferBuilder buffer, float u0, float v0, float r, float g, float b, float alpha,
                             float nx, float ny, float nz, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, float du, float dv) {
        vertex(buffer, u0, v0, r, g, b, alpha, nx, ny, nz, x1, y1, z1, du, dv);
        vertex(buffer, u0, v0, r, g, b, alpha, nx, ny, nz, x2, y2, z2, du, dv);
        vertex(buffer, u0, v0, r, g, b, alpha, nx, ny, nz, x3, y3, z3, du, dv);
        vertex(buffer, u0, v0, r, g, b, alpha, nx, ny, nz, x4, y4, z4, du, dv);
    }

    private static void vertex(BufferBuilder buffer, float u0, float v0, float r, float g, float b, float alpha,
                               float nx, float ny, float nz, float x, float y, float z, float du, float dv) {
        buffer.addVertex(x, y, z).setUv((x + du) * TEXEL + u0, (z + dv) * TEXEL + v0).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
    }

    private static float[] rgb(int rgb) {
        return new float[]{(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
    }

    private static float[] shade(float[] seen, float face) {
        return new float[]{seen[0] / face, seen[1] / face, seen[2] / face};
    }
}

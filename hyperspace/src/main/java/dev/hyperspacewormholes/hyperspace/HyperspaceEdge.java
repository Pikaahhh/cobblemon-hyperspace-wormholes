package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the island's edge: a curtain of Hyperspace sky rising from the drop and a glowing rainbow strip along the
 * ground.
 */
public class HyperspaceEdge {
    private static final int HALF = 52;
    private static final float NO_LINE = 4095f;
    private static final int SIZE = HALF * 2 + 1;
    private static final float CURTAIN_DEPTH = 64f;
    private static final int RES = 4;
    private static final float FAR = 4f;
    private static final float CREEP = 0.5f;
    private static final float CREEP_SECONDS = 7f;
    private static final int GAP = 24;
    private static final int STEP = 2;
    private static final int RUN = 32;
    private static final float[][] BANDS = {
            {0f, 1.1f, 0.2f, 0f, -1},
            {-0.34f, -0.08f, 0f, 0.65f, 1f, 0.4f, 0.9f}, {0.08f, 0.34f, 0.65f, 0f, 0.35f, 0.9f, 1f},
            {-0.08f, 0.08f, 1f, 1f, 1f, 1f, 1f}
    };

    private static final Map<Integer, Edge> edges = new HashMap<>();
    private static Edge current, meshed;
    private static long changes, missed = -1;
    private static int missedSlot;
    private static DynamicTexture mask;
    private static float creep;

    private static ShaderInstance curtainShader;
    private static VertexBuffer curtainMesh;
    private static boolean curtainEmpty = true;
    private static Frustum culling;
    private static final float[] FROM = cornerSlots(), TO = cornerSlots(), LOW = cornerSlots(), HIGH = cornerSlots();

    private static class Edge {
        int x0, z0;
        int[][] height = new int[SIZE][SIZE];
        boolean[][] solid = new boolean[SIZE][SIZE];
        int[][] lowest = new int[SIZE][SIZE];
        float[][] snow = new float[SIZE][SIZE];
        int[][][] surfaces = new int[SIZE][SIZE][];
        long built, seen;
        List<float[][]> loops = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
    }

    private record Line(float[][] points, boolean closed, AABB[] runs) {
    }

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Hyperspace.id("edge_curtain"), DefaultVertexFormat.POSITION,
                shader -> curtainShader = shader));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> changed());
    }
    public static void changed() {
        changes++;
    }

    public static void prepare(WorldRenderContext context) {
        ClientLevel level = context.world();
        if (level == null || !level.dimension().equals(Hyperspace.HYPERSPACE)) {
            edges.clear();
            current = null;
            missed = -1;
            return;
        }
        Vec3 cam = context.camera().getPosition();
        double cycle = (level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false)) / 20.0 / CREEP_SECONDS;
        creep = (float) (cycle - Math.floor(cycle));

        Edge edge = null;
        int slot = (int) Math.round(cam.x / Zones.SPACING);
        if (Math.abs(cam.z - Zones.ROW_Z) <= 160 && slot >= 0 && slot < Zones.SLOTS) {
            edge = edges.get(Zones.BASE + slot);
            if (edge == null ? missed != changes || missedSlot != slot : level.getGameTime() - edge.built > 40) {
                if (edge != null && edge.seen == changes) {
                    edge.built = level.getGameTime();
                } else {
                    Edge fresh = build(level, Zones.centre(slot), edge);
                    if (fresh != null) {
                        edges.put(Zones.BASE + slot, edge = fresh);
                    } else if (edge == null) {
                        missed = changes;
                        missedSlot = slot;
                    }
                }
            }
        }
        if (edge != null && edge != current) fillMask(edge);
        current = edge;
    }

    public static void uniforms(EffectInstance effect, WorldRenderContext context) {
        Uniform info = effect.getUniform("MaskInfo");
        if (info == null) return;
        if (current == null) {
            info.set(0f, RES, 1f, 0f);
            return;
        }
        Vec3 cam = context.camera().getPosition();
        float ticks = context.world().getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        effect.getUniform("InverseView").set(new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix()).invert());
        effect.getUniform("MaskOrigin").set((float) (current.x0 - cam.x), (float) -cam.y, (float) (current.z0 - cam.z));
        effect.getUniform("SkyAngle").set(HyperspaceSky.angle(ticks));
        info.set(1f, RES, SIZE * RES, creep);
        int maskId = mask.getId();
        int skyId = Minecraft.getInstance().getTextureManager().getTexture(HyperspaceSky.TEXTURE).getId();
        effect.setSampler("MaskSampler", () -> maskId);
        effect.setSampler("SkySampler", () -> skyId);
    }

    private static void fillMask(Edge edge) {
        int n = SIZE * RES;
        if (mask == null) mask = new DynamicTexture(n, n, false);
        float[] height = new float[n * n];
        float[] distance = field(edge, height);
        NativeImage image = mask.getPixels();
        for (int tx = 0; tx < n; tx++) {
            for (int tz = 0; tz < n; tz++) {
                int d = Mth.clamp(Math.round((distance[tx * n + tz] + 8f) / 16f * 65535f), 0, 65535);
                int h = Mth.clamp(Math.round((Math.min(height[tx * n + tz], NO_LINE) + 2048f) * 16f), 0, 65535);
                image.setPixelRGBA(tx, tz, ((h & 255) << 24) | ((h >> 8) << 16) | ((d & 255) << 8) | (d >> 8));
            }
        }
        mask.upload();
    }

    private static float[] field(Edge edge, float[] height) {
        int n = SIZE * RES;
        float[] distance = new float[n * n];
        Arrays.fill(height, Float.MAX_VALUE);
        for (int tx = 0; tx < n; tx++) {
            for (int tz = 0; tz < n; tz++) distance[tx * n + tz] = edge.solid[tx / RES][tz / RES] ? -FAR : FAR;
        }
        for (float[][] loop : edge.loops) {
            for (int i = 0; i < loop.length; i++) {
                float[] p = loop[i], q = loop[(i + 1) % loop.length];
                float ex = q[0] - p[0], ez = q[1] - p[1];
                float length = ex * ex + ez * ez;
                float nx = ez, nz = -ex;
                if (nx * p[3] + nz * p[4] < 0) {
                    nx = -nx;
                    nz = -nz;
                }
                int x0 = Math.max(0, (int) Math.floor((Math.min(p[0], q[0]) - FAR - edge.x0) * RES));
                int x1 = Math.min(n - 1, (int) Math.ceil((Math.max(p[0], q[0]) + FAR - edge.x0) * RES));
                int z0 = Math.max(0, (int) Math.floor((Math.min(p[1], q[1]) - FAR - edge.z0) * RES));
                int z1 = Math.min(n - 1, (int) Math.ceil((Math.max(p[1], q[1]) + FAR - edge.z0) * RES));
                for (int tx = x0; tx <= x1; tx++) {
                    for (int tz = z0; tz <= z1; tz++) {
                        float wx = edge.x0 + (tx + 0.5f) / RES, wz = edge.z0 + (tz + 0.5f) / RES;
                        float u = length == 0 ? 0 : Mth.clamp(((wx - p[0]) * ex + (wz - p[1]) * ez) / length, 0f, 1f);
                        float dx = wx - (p[0] + ex * u), dz = wz - (p[1] + ez * u);
                        float d = (float) Math.sqrt(dx * dx + dz * dz);
                        int k = tx * n + tz;
                        if (d >= Math.abs(distance[k])) continue;
                        distance[k] = dx * nx + dz * nz >= 0 ? d : -d;
                        height[k] = Math.max(p[2], q[2]);
                    }
                }
            }
        }
        return distance;
    }

    private static List<List<Long>> walk(Map<Long, List<Long>> links) {
        List<List<Long>> paths = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        List<Long> starts = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : links.entrySet()) {
            if (entry.getValue().size() == 1) starts.add(entry.getKey());
        }
        starts.addAll(links.keySet());
        for (long start : starts) {
            if (used.contains(start)) continue;
            List<Long> path = new ArrayList<>();
            long prev = Long.MIN_VALUE, at = start;
            while (true) {
                path.add(at);
                used.add(at);
                long next = Long.MIN_VALUE;
                for (long n : links.get(at)) {
                    if (n != prev && (!used.contains(n) || (n == start && path.size() > 2))) {
                        next = n;
                        break;
                    }
                }
                if (next == Long.MIN_VALUE || next == start) break;
                prev = at;
                at = next;
            }
            paths.add(path);
        }
        return paths;
    }

    public static void render(WorldRenderContext context) {
        if (current == null) return;
        Vec3 cam = context.camera().getPosition();
        float ticks = context.world().getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        float time = ticks / 20f;

        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        culling = context.frustum();
        if (meshed != current) {
            meshed = current;
            BufferBuilder space = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            for (Line line : current.lines) {
                float[][] loop = line.points();
                for (int i = 0; i < (line.closed() ? loop.length : loop.length - 1); i++) {
                    float[] p = loop[i], q = loop[(i + 1) % loop.length];
                    if (p[2] == q[2]) {
                        curtain(space, p, q, current);
                    } else {
                        curtain(space, p, between(p, q, q[5], p[2]), current);
                        curtain(space, between(p, q, q[5], q[2]), q, current);
                    }
                }
            }
            MeshData spaceMesh = space.build();
            curtainEmpty = spaceMesh == null;
            if (spaceMesh != null) {
                if (curtainMesh == null) curtainMesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
                curtainMesh.bind();
                curtainMesh.upload(spaceMesh);
                VertexBuffer.unbind();
            }
        }
        if (!curtainEmpty && curtainShader != null) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, HyperspaceSky.TEXTURE);
            curtainShader.safeGetUniform("CornerOffset").set((float) (current.x0 - cam.x), (float) -cam.y, (float) (current.z0 - cam.z));
            curtainShader.safeGetUniform("SkyAngle").set(HyperspaceSky.angle(ticks));
            curtainMesh.bind();
            curtainMesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), curtainShader);
            VertexBuffer.unbind();
        }

        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Line line : current.lines) ribbon(glow, line, cam, time);
        MeshData glowMesh = glow.build();
        if (glowMesh != null) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(glowMesh);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void curtain(BufferBuilder buffer, float[] p, float[] q, Edge edge) {
        float px = p[0] - edge.x0 + p[3] * 0.06f, pz = p[1] - edge.z0 + p[4] * 0.06f;
        float qx = q[0] - edge.x0 + q[3] * 0.06f, qz = q[1] - edge.z0 + q[4] * 0.06f;
        float top = -0.02f, bottom = top - CURTAIN_DEPTH;
        buffer.addVertex(px, p[2] + top, pz);
        buffer.addVertex(qx, q[2] + top, qz);
        buffer.addVertex(qx, q[2] + bottom, qz);
        buffer.addVertex(px, p[2] + bottom, pz);
    }

    private static Edge build(ClientLevel level, BlockPos centre, Edge last) {
        Edge edge = new Edge();
        edge.x0 = centre.getX() - HALF;
        edge.z0 = centre.getZ() - HALF;
        for (int cx = edge.x0 >> 4; cx <= (edge.x0 + SIZE) >> 4; cx++) {
            for (int cz = edge.z0 >> 4; cz <= (edge.z0 + SIZE) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return null;
            }
        }
        int floor = level.getMinBuildHeight();
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                scan(level, edge, x, z, floor, pos);
                any |= edge.solid[x][z];
            }
        }
        if (!any) return null;

        if (last != null && same(edge, last)) {
            edge = last;
        } else {
        trace(edge);
        layers(edge);
        }
        edge.built = level.getGameTime();
        edge.seen = changes;
        return edge;
    }
    private static boolean same(Edge a, Edge b) {
        return a.x0 == b.x0 && a.z0 == b.z0 && Arrays.deepEquals(a.solid, b.solid) && Arrays.deepEquals(a.height, b.height)
                && Arrays.deepEquals(a.lowest, b.lowest) && Arrays.deepEquals(a.snow, b.snow)
                && Arrays.deepEquals(a.surfaces, b.surfaces);
    }

    private static void scan(ClientLevel level, Edge edge, int x, int z, int floor, BlockPos.MutableBlockPos pos) {
        int wx = edge.x0 + x, wz = edge.z0 + z;
        List<Integer> found = new ArrayList<>();
        int lowest = Integer.MAX_VALUE, gap = 0;
        boolean above = false;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
        edge.snow[x][z] = snow(level.getBlockState(pos.set(wx, top, wz)));
        for (int y = top - 1; y >= floor; y--) {
            BlockState state = level.getBlockState(pos.set(wx, y, wz));
            if (state.is(Blocks.SNOW) && lowest == Integer.MAX_VALUE) {
                edge.snow[x][z] = snow(state);
                continue;
            }
            boolean here = !state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                    && !state.getCollisionShape(level, pos).isEmpty();
            if (here) {
                if (!above) found.add(y + 1);
                lowest = y;
                gap = 0;
            } else if (lowest != Integer.MAX_VALUE && ++gap > GAP) {
                break;
            }
            above = here;
        }
        int[] surfaces = found.stream().mapToInt(Integer::intValue).toArray();
        edge.surfaces[x][z] = surfaces;
        edge.lowest[x][z] = lowest;
        edge.solid[x][z] = surfaces.length > 0;
        edge.height[x][z] = surfaces.length > 0 ? surfaces[0] : floor;
    }

    private static float snow(BlockState state) {
        return state.is(Blocks.SNOW) ? state.getValue(SnowLayerBlock.LAYERS) / 8f : 0;
    }

    /** The snow lying on this surface round a corner: the line sits on top of it, or a thin layer hides it. */
    private static float snowAt(Edge edge, int cx, int cz, int h) {
        float most = 0;
        for (int x = Math.max(cx - 1, 0); x <= Math.min(cx, SIZE - 1); x++) {
            for (int z = Math.max(cz - 1, 0); z <= Math.min(cz, SIZE - 1); z++) {
                if (edge.height[x][z] == h) most = Math.max(most, edge.snow[x][z]);
            }
        }
        return most;
    }

    private static boolean open(Edge edge, int x, int z, float height) {
        return x < 0 || z < 0 || x >= SIZE || z >= SIZE || edge.lowest[x][z] >= height;
    }

    private static long corner(int x, int z, int h) {
        return ((long) x << 40) | ((long) z << 20) | (h + 4096);
    }

    private static void join(Map<Long, List<Long>> links, long a, long b) {
        links.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        links.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }

    private static void layers(Edge edge) {
        Map<Long, List<Long>> links = new HashMap<>();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[][] bottom = new int[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                bottom[x][z] = Integer.MAX_VALUE;
                for (int h : edge.surfaces[x][z]) {
                    for (int[] s : sides) {
                        if (open(edge, x + s[0], z + s[1], h)) bottom[x][z] = Math.min(bottom[x][z], h);
                    }
                }
            }
        }
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int h : edge.surfaces[x][z]) {
                    if (bottom[x][z] < h - STEP) continue;
                    for (int[] s : sides) {
                        if (!open(edge, x + s[0], z + s[1], h)) continue;
                        int ax = s[0] > 0 ? x + 1 : x, az = s[1] > 0 ? z + 1 : z;
                        int bx = s[0] != 0 ? ax : x + 1, bz = s[1] != 0 ? az : z + 1;
                        join(links, corner(ax, az, h), corner(bx, bz, h));
                    }
                }
            }
        }
        Map<Long, List<Integer>> loose = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : links.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            long k = entry.getKey();
            loose.computeIfAbsent(k >> 20, c -> new ArrayList<>()).add((int) (k & 0xFFFFF) - 4096);
        }
        for (Map.Entry<Long, List<Integer>> entry : loose.entrySet()) {
            List<Integer> heights = entry.getValue();
            heights.sort(null);
            for (int i = 0; i + 1 < heights.size(); i++) {
                if (heights.get(i + 1) - heights.get(i) > STEP) continue;
                long c = entry.getKey() << 20;
                join(links, c | (heights.get(i) + 4096), c | (heights.get(i + 1) + 4096));
                i++;
            }
        }

        for (List<Long> path : walk(links)) {
            if (path.size() < 3) continue;
            boolean closed = links.get(path.get(path.size() - 1)).contains(path.get(0));
            float[][] points = new float[path.size()][];
            for (int i = 0; i < path.size(); i++) {
                long k = path.get(i);
                int cx = (int) (k >> 40), cz = (int) ((k >> 20) & 0xFFFFF), h = (int) (k & 0xFFFFF) - 4096;
                points[i] = new float[]{edge.x0 + cx, edge.z0 + cz, h + snowAt(edge, cx, cz, h)};
            }
            edge.lines.add(line(finish(edge, chaikin(chaikin(points, closed), closed), closed), closed));
        }
    }

    private static float phase(Edge edge, float x, float z) {
        float theta = (float) Math.atan2(z - (edge.z0 + HALF + 0.5f), x - (edge.x0 + HALF + 0.5f));
        return 7 * theta / Mth.TWO_PI + 0.25f * Mth.sin(12 * theta) + 0.15f * Mth.sin(30 * theta + 1.3f);
    }

    private static boolean solidAt(Edge edge, int x, int z) {
        return x >= 0 && z >= 0 && x < SIZE && z < SIZE && edge.solid[x][z];
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static void link(Map<Long, List<Long>> links, Map<Long, Integer> heights, int[] a, int[] b) {
        long ka = key(a[0], a[1]), kb = key(b[0], b[1]);
        join(links, ka, kb);
        heights.putIfAbsent(ka, a[2]);
        heights.putIfAbsent(kb, b[2]);
    }

    private static int[] mid(Edge edge, int x1, int z1, int x2, int z2) {
        int h = solidAt(edge, x1, z1) ? edge.height[x1][z1] : edge.height[x2][z2];
        return new int[]{x1 + x2 + 1, z1 + z2 + 1, h};
    }

    private static void trace(Edge edge) {
        Map<Long, List<Long>> links = new HashMap<>();
        Map<Long, Integer> heights = new HashMap<>();
        for (int x = -1; x < SIZE; x++) {
            for (int z = -1; z < SIZE; z++) {
                boolean a = solidAt(edge, x, z), b = solidAt(edge, x + 1, z), c = solidAt(edge, x + 1, z + 1), d = solidAt(edge, x, z + 1);
                int[] e0 = a != b ? mid(edge, x, z, x + 1, z) : null;
                int[] e1 = b != c ? mid(edge, x + 1, z, x + 1, z + 1) : null;
                int[] e2 = d != c ? mid(edge, x, z + 1, x + 1, z + 1) : null;
                int[] e3 = a != d ? mid(edge, x, z, x, z + 1) : null;
                if (e0 != null && e1 != null && e2 != null && e3 != null) {
                    if (a) {
                        link(links, heights, e0, e3);
                        link(links, heights, e1, e2);
                    } else {
                        link(links, heights, e0, e1);
                        link(links, heights, e2, e3);
                    }
                    continue;
                }
                int[] first = null;
                for (int[] e : new int[][]{e0, e1, e2, e3}) {
                    if (e == null) continue;
                    if (first == null) first = e;
                    else link(links, heights, first, e);
                }
            }
        }

        for (List<Long> path : walk(links)) {
            if (path.size() < 8) continue;
            float[][] points = new float[path.size()][];
            for (int i = 0; i < path.size(); i++) {
                long k = path.get(i);
                int kx = (int) (k >> 32), kz = (int) k;
                points[i] = new float[]{edge.x0 + kx / 2f, edge.z0 + kz / 2f, heights.get(k)};
            }
            edge.loops.add(finish(edge, chaikin(chaikin(points, true), true), true));
        }
    }

    private static float[][] chaikin(float[][] points, boolean closed) {
        int n = points.length;
        List<float[]> out = new ArrayList<>();
        if (!closed) out.add(points[0]);
        for (int i = 0; i < (closed ? n : n - 1); i++) {
            float[] p = points[i], q = points[(i + 1) % n];
            out.add(new float[]{p[0] * 0.75f + q[0] * 0.25f, p[1] * 0.75f + q[1] * 0.25f, p[2]});
            out.add(new float[]{p[0] * 0.25f + q[0] * 0.75f, p[1] * 0.25f + q[1] * 0.75f, q[2]});
        }
        if (!closed) out.add(points[n - 1]);
        return out.toArray(new float[0][]);
    }

    private static float[] away(float[][] points, int i, int step, boolean closed) {
        int n = points.length;
        float[] p = points[i];
        for (int k = 1; k <= 4; k++) {
            int j = i + step * k;
            if (closed) j = ((j % n) + n) % n;
            else if (j < 0 || j >= n) break;
            if (Math.abs(points[j][0] - p[0]) + Math.abs(points[j][1] - p[1]) > 1.0E-3f) return points[j];
        }
        return p;
    }

    private static float[][] finish(Edge edge, float[][] points, boolean closed) {
        int n = points.length;
        float[][] out = new float[n][];
        float s = 0, lastX = 0, lastZ = 0;
        for (int i = 0; i < n; i++) {
            float[] p = points[i], before = away(points, i, -1, closed), after = away(points, i, 1, closed);
            if (i > 0) s += (float) Math.hypot(p[0] - points[i - 1][0], p[1] - points[i - 1][1]);
            float tx = after[0] - before[0], tz = after[1] - before[1];
            float len = (float) Math.hypot(tx, tz);
            float nx = len == 0 ? lastX : tz / len, nz = len == 0 ? lastZ : -tx / len;
            int sx = (int) Math.floor(p[0] + nx * 0.6f) - edge.x0, sz = (int) Math.floor(p[1] + nz * 0.6f) - edge.z0;
            if (len != 0 && !open(edge, sx, sz, p[2])) {
                nx = -nx;
                nz = -nz;
            }
            lastX = nx;
            lastZ = nz;
            out[i] = new float[]{p[0], p[1], p[2], nx, nz, s, phase(edge, p[0], p[1])};
        }
        return out;
    }

    private static Line line(float[][] points, boolean closed) {
        int segments = closed ? points.length : points.length - 1;
        AABB[] runs = new AABB[(segments + RUN - 1) / RUN];
        for (int i = 0; i < segments; i++) {
            AABB box = box(points[i], points[(i + 1) % points.length]);
            runs[i / RUN] = i % RUN == 0 ? box : runs[i / RUN].minmax(box);
        }
        return new Line(points, closed, runs);
    }
    private static AABB box(float[] p, float[] q) {
        return new AABB(Math.min(p[0], q[0]) - 0.5, Math.min(p[2], q[2]), Math.min(p[1], q[1]) - 0.5,
                Math.max(p[0], q[0]) + 0.5, Math.max(p[2], q[2]) + 0.2, Math.max(p[1], q[1]) + 0.5);
    }
    private static void ribbon(BufferBuilder buffer, Line line, Vec3 cam, float time) {
        float[][] loop = line.points();
        int n = loop.length;
        float[] from = FROM, to = TO;
        int ready = -1;
        for (int i = 0; i < (line.closed() ? n : n - 1); i++) {
            if (culling != null && i % RUN == 0 && !culling.isVisible(line.runs()[i / RUN])) {
                i += RUN - 1;
                continue;
            }
            float[] p = loop[i], q = loop[(i + 1) % n];
            if (culling != null && !culling.isVisible(box(p, q))) continue;
            float sq = i + 1 == n ? p[5] + (float) Math.hypot(q[0] - p[0], q[1] - p[1]) : q[5];
            if (ready != i) corners(from, p, p[5], cam, time);
            corners(to, q, sq, cam, time);
            if (p[2] == q[2]) {
                strip(buffer, from, to);
            } else {
                float[] low = between(p, q, sq, p[2]), high = between(p, q, sq, q[2]);
                corners(LOW, low, low[5], cam, time);
                corners(HIGH, high, high[5], cam, time);
                strip(buffer, from, LOW);
                strip(buffer, HIGH, to);
                strip(buffer, LOW, HIGH);
            }
            float[] drawn = from;
            from = to;
            to = drawn;
            ready = i + 1;
        }
    }

    private static void strip(BufferBuilder buffer, float[] from, float[] to) {
        for (int k = 0; k < from.length; k += 14) {
            vertex(buffer, from, k);
            vertex(buffer, to, k);
            vertex(buffer, to, k + 7);
            vertex(buffer, from, k + 7);
        }
    }

    private static void vertex(BufferBuilder buffer, float[] corner, int k) {
        buffer.addVertex(corner[k], corner[k + 1], corner[k + 2]).setColor(corner[k + 3], corner[k + 4], corner[k + 5], corner[k + 6]);
    }

    private static float[] between(float[] p, float[] q, float sq, float height) {
        float nx = p[3] + q[3], nz = p[4] + q[4];
        float len = (float) Math.hypot(nx, nz);
        if (len == 0) {
            nx = p[3];
            nz = p[4];
        } else {
            nx /= len;
            nz /= len;
        }
        float phase = q[6] - p[6];
        phase -= Math.round(phase);
        return new float[]{(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, height, nx, nz, (p[5] + sq) / 2, p[6] + phase / 2};
    }

    private static void corners(float[] out, float[] p, float s, Vec3 cam, float time) {
        float shift = CREEP * (0.5f - 0.5f * Mth.cos((creep + p[6]) * Mth.TWO_PI));
        float wave = 0.65f + 0.35f * Mth.sin(s * 0.3f - time * 3.5f);
        float px = p[0], pz = p[1], nx = p[3], nz = p[4], y = (float) (p[2] + 0.06 - cam.y);
        int k = 0;
        for (float[] band : BANDS) {
            for (int side = 0; side < 2; side++, k += 7) {
                float d = band[side] - shift;
                double x = px + nx * d, z = pz + nz * d;
                float a = Mth.clamp(band[side + 2] * wave, 0f, 1f);
                out[k] = (float) (x - cam.x);
                out[k + 1] = y;
                out[k + 2] = (float) (z - cam.z);
                if (band[4] >= 0) {
                    out[k + 6] = a;
                    continue;
                }
                float hue = (float) (time * 0.04 + x * 0.035 + z * 0.025 + 0.12 * Math.sin(time * 0.8 + x * 0.21 - z * 0.17));
                hue -= (float) Math.floor(hue);
                float pulse = 0.8f + 0.2f * Mth.sin((float) (time * 2.2 + (x + z) * 0.45));
                int rgb = Mth.hsvToRgb(hue, 0.8f, 1f);
                out[k + 3] = ((rgb >> 16) & 255) / 255f;
                out[k + 4] = ((rgb >> 8) & 255) / 255f;
                out[k + 5] = (rgb & 255) / 255f;
                out[k + 6] = Mth.clamp(a * pulse, 0f, 1f);
            }
        }
    }

    private static float[] cornerSlots() {
        float[] slots = new float[BANDS.length * 14];
        for (int b = 0; b < BANDS.length; b++) {
            if (BANDS[b][4] < 0) continue;
            for (int k = b * 14; k < b * 14 + 14; k += 7) {
                slots[k + 3] = BANDS[b][4];
                slots[k + 4] = BANDS[b][5];
                slots[k + 5] = BANDS[b][6];
            }
        }
        return slots;
    }
}

package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.LuminescentIce;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Luminescent Ice's smooth height gradient in the world. A block tint is one colour for the whole block, so every placed
 * Luminescent Ice model (the block, slab and stairs) is wrapped: each face is cut into BANDS strips a block, every corner
 * coloured by LuminescentIce.colour at its exact world height, and the renderer blends between corners. The quads drop
 * their tint index so the per-block colour provider (kept for break particles) doesn't colour them again.
 */
public final class LuminescentIceModel extends ForwardingBakedModel {
    private static final int BANDS = 4;
    private static final float EPSILON = 1.0e-4f;
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};
    private final RenderMaterial material = RendererAccess.INSTANCE.getRenderer().materialFinder().find();

    private LuminescentIceModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    public static void register() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            ModelResourceLocation id = context.topLevelId();
            boolean ice = model != null && id != null && !id.variant().equals("inventory")
                    && id.id().getNamespace().equals(Wormholes.MOD_ID) && id.id().getPath().startsWith("luminescent_ice");
            return ice ? new LuminescentIceModel(model) : model;
        }));
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random,
                               RenderContext context) {
        QuadEmitter emitter = context.getEmitter();
        for (Direction face : FACES) {
            for (BakedQuad quad : wrapped.getQuads(state, face, random.get())) {
                emit(emitter, quad, face, pos.getY());
            }
        }
    }

    private void emit(QuadEmitter emitter, BakedQuad quad, Direction face, int blockY) {
        emitter.fromVanilla(quad, material, face);
        float[][] corners = new float[4][];
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            corners[v] = new float[]{emitter.x(v), emitter.y(v), emitter.z(v), emitter.u(v), emitter.v(v)};
            low = Math.min(low, corners[v][1]);
            high = Math.max(high, corners[v][1]);
        }
        if (high - low < EPSILON) {
            for (int v = 0; v < 4; v++) emitter.color(v, LuminescentIce.colour(blockY + corners[v][1]));
            emitter.colorIndex(-1).emit();
            return;
        }
        for (float from = low; from < high - EPSILON; ) {
            float to = Math.min(high, ((float) Math.floor(from * BANDS + EPSILON) + 1) / BANDS);
            emitter.fromVanilla(quad, material, face);
            for (int v = 0; v < 4; v++) {
                float[] self = corners[v];
                float[] other = corners[partner(corners, v)];
                boolean top = self[1] > other[1];
                float[] bottom = top ? other : self;
                float[] upper = top ? self : other;
                float y = top ? to : from;
                float t = (y - low) / (high - low);
                emitter.pos(v, Mth.lerp(t, bottom[0], upper[0]), y, Mth.lerp(t, bottom[2], upper[2]));
                emitter.uv(v, Mth.lerp(t, bottom[3], upper[3]), Mth.lerp(t, bottom[4], upper[4]));
                emitter.color(v, LuminescentIce.colour(blockY + y));
            }
            emitter.colorIndex(-1).emit();
            from = to;
        }
    }

    private static int partner(float[][] corners, int v) {
        for (int o = 0; o < 4; o++) {
            if (o != v && Math.abs(corners[o][0] - corners[v][0]) < EPSILON && Math.abs(corners[o][2] - corners[v][2]) < EPSILON
                    && Math.abs(corners[o][1] - corners[v][1]) >= EPSILON) {
                return o;
            }
        }
        return v;
    }
}

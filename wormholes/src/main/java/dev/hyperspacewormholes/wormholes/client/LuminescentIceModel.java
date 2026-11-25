package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.LuminescentIce;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Luminescent Ice's smooth height gradient in the world. A block tint is one colour for the whole block, so every placed
 * Luminescent Ice model (the block, slab and stairs) is wrapped: each face is cut into BANDS strips a block, every corner
 * coloured by LuminescentIce.colour at its exact world height, and the renderer blends between corners. The quads drop
 * their tint index so the per-block colour provider (kept for break particles) doesn't colour them again.
 */
public final class LuminescentIceModel extends BakedModelWrapper<BakedModel> {
    private static final int BANDS = 4;
    private static final float EPSILON = 1.0e-4f;
    private static final int STRIDE = 8, COLOUR = 3, UV = 4;
    private static final ModelProperty<Integer> BLOCK_Y = new ModelProperty<>();

    private LuminescentIceModel(BakedModel wrapped) {
        super(wrapped);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelEvent.ModifyBakingResult.class, event -> event.getModels().replaceAll((id, model) -> {
            boolean ice = model != null && id != null && !id.variant().equals("inventory")
                    && id.id().getNamespace().equals(Wormholes.MOD_ID) && id.id().getPath().startsWith("luminescent_ice");
            return ice ? new LuminescentIceModel(model) : model;
        }));
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return super.getModelData(level, pos, state, modelData).derive().with(BLOCK_Y, pos.getY()).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        Integer blockY = data.get(BLOCK_Y);
        if (blockY == null) return quads;
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad quad : quads) emit(out, quad, blockY);
        return out;
    }

    private static void emit(List<BakedQuad> out, BakedQuad quad, int blockY) {
        int[] vertices = quad.getVertices();
        float[][] corners = new float[4][];
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            int o = v * STRIDE;
            corners[v] = new float[]{Float.intBitsToFloat(vertices[o]), Float.intBitsToFloat(vertices[o + 1]),
                    Float.intBitsToFloat(vertices[o + 2]), Float.intBitsToFloat(vertices[o + UV]),
                    Float.intBitsToFloat(vertices[o + UV + 1])};
            low = Math.min(low, corners[v][1]);
            high = Math.max(high, corners[v][1]);
        }
        if (high - low < EPSILON) {
            int[] copy = vertices.clone();
            for (int v = 0; v < 4; v++) copy[v * STRIDE + COLOUR] = colour(blockY + corners[v][1]);
            out.add(copy(quad, copy));
            return;
        }
        for (float from = low; from < high - EPSILON; ) {
            float to = Math.min(high, ((float) Math.floor(from * BANDS + EPSILON) + 1) / BANDS);
            int[] copy = vertices.clone();
            for (int v = 0; v < 4; v++) {
                float[] self = corners[v];
                float[] other = corners[partner(corners, v)];
                boolean top = self[1] > other[1];
                float[] bottom = top ? other : self;
                float[] upper = top ? self : other;
                float y = top ? to : from;
                float t = (y - low) / (high - low);
                int o = v * STRIDE;
                copy[o] = Float.floatToRawIntBits(Mth.lerp(t, bottom[0], upper[0]));
                copy[o + 1] = Float.floatToRawIntBits(y);
                copy[o + 2] = Float.floatToRawIntBits(Mth.lerp(t, bottom[2], upper[2]));
                copy[o + UV] = Float.floatToRawIntBits(Mth.lerp(t, bottom[3], upper[3]));
                copy[o + UV + 1] = Float.floatToRawIntBits(Mth.lerp(t, bottom[4], upper[4]));
                copy[o + COLOUR] = colour(blockY + y);
            }
            out.add(copy(quad, copy));
            from = to;
        }
    }

    /** The quad with new vertices and no tint index. */
    private static BakedQuad copy(BakedQuad quad, int[] vertices) {
        return new BakedQuad(vertices, -1, quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static int colour(float y) {
        return FastColor.ABGR32.fromArgb32(LuminescentIce.colour(y));
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

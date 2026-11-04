package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Buffers that keep each vertex's alpha but lose its colour, so a Pokemon's model draws as a flat black silhouette. */
public record SilhouetteBuffers(MultiBufferSource inner) implements MultiBufferSource {
    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Black(inner.getBuffer(type));
    }

    private record Black(VertexConsumer inner) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            inner.setColor(0, 0, 0, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}

package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of draws of our own at a chosen point of the frame (Light That Burns the Sky, after the world): batches are
 * filled first, each with its render type and view matrix and any number of them at once, then drawn in the order they
 * were begun. Each batch has a buffer of its own, kept across frames so they aren't allocated again.
 */
public final class OwnPass {
    private record Batch(RenderType type, BufferBuilder builder, Matrix4f modelView) {}

    private final List<ByteBufferBuilder> buffers = new ArrayList<>();
    private final List<Batch> batches = new ArrayList<>();

    public VertexConsumer begin(RenderType type, Matrix4f modelView) {
        int index = batches.size();
        if (index == buffers.size()) buffers.add(new ByteBufferBuilder(1 << 16));
        BufferBuilder builder = new BufferBuilder(buffers.get(index), type.mode(), type.format());
        batches.add(new Batch(type, builder, modelView));
        return builder;
    }

    public void draw() {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        // A render type with no depth test leaves it as it found it, so it's off between batches.
        RenderSystem.disableDepthTest();
        stack.pushMatrix();
        for (Batch batch : batches) {
            MeshData mesh = batch.builder().build();
            if (mesh == null) continue;
            stack.set(batch.modelView());
            RenderSystem.applyModelViewMatrix();
            batch.type().draw(mesh);
        }
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        batches.clear();
    }
}

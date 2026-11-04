package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.hyperspacewormholes.wormholes.ride.PlantXurkitrees;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Ultra Plant's statue Xurkitrees (aspect PlantXurkitrees.STATUE) drawn from meshes kept on the graphics card: they never move, but Cobblemon would pose their 264 bones and send all 419 cubes again every frame.
 * The first time one is drawn, Cobblemon's renderer draws it once into buffers of ours (one per render type, so the glow layer too) that are kept as vertex buffers, and from then on only those are drawn where it stands (mixin/StatueMeshMixin).
 * It is rebuilt when its light, turn or size differs from the kept one's, drawn Cobblemon's way once it leaves the pose (the zone's Xurkitree waking) or while an Iris shaderpack is on, and meshes go when the statue unloads or the world changes.
 */
public final class StatueMeshes {
    private record Part(RenderType type, VertexBuffer buffer) {}

    private record Kept(List<Part> parts, int light, float turn, float height) {
        boolean fits(PokemonEntity entity, int light) {
            return this.light == light && turn == entity.yBodyRot && height == entity.getBbHeight();
        }

        void close() {
            parts.forEach(part -> part.buffer().close());
        }
    }

    private static final Map<PokemonEntity, Kept> KEPT = new IdentityHashMap<>();
    private static @Nullable ClientLevel level;

    private StatueMeshes() {}

    public static void register() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> drop(entity));
        WorldRenderEvents.START.register(context -> {
            if (context.world() == level) return;
            KEPT.values().forEach(Kept::close);
            KEPT.clear();
            level = context.world();
        });
    }

    private static void drop(Entity entity) {
        Kept kept = KEPT.remove(entity);
        if (kept != null) kept.close();
    }

    public static boolean draw(Entity entity, PoseStack pose, int light, Consumer<MultiBufferSource> render) {
        if (!(entity instanceof PokemonEntity pokemon)) return false;
        if (!pokemon.getAspects().contains(PlantXurkitrees.STATUE) || ShaderPacks.inUse()) {
            drop(pokemon);
            return false;
        }
        Kept kept = KEPT.get(pokemon);
        if (kept == null || !kept.fits(pokemon, light)) {
            drop(pokemon);
            kept = keep(pokemon, light, render);
            KEPT.put(pokemon, kept);
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        for (Part part : kept.parts()) {
            part.type().setupRenderState();
            part.buffer().bind();
            part.buffer().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            part.type().clearRenderState();
        }
        VertexBuffer.unbind();
        return true;
    }

    private static Kept keep(PokemonEntity entity, int light, Consumer<MultiBufferSource> render) {
        Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        render.accept(type -> builders.computeIfAbsent(type, t -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 16);
            memory.put(t, bytes);
            return new BufferBuilder(bytes, t.mode(), t.format());
        }));
        List<Part> parts = new ArrayList<>();
        builders.forEach((type, builder) -> {
            MeshData mesh = builder.build();
            if (mesh == null) return;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            parts.add(new Part(type, buffer));
        });
        VertexBuffer.unbind();
        memory.values().forEach(ByteBufferBuilder::close);
        return new Kept(parts, light, entity.yBodyRot, entity.getBbHeight());
    }
}

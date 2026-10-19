package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.lang.reflect.Method;

/**
 * Checks if an Iris shaderpack is on (through Iris's API with reflection) and draws stuff in the world camera's view
 * after the world's done rendering.
 */
public final class ShaderPacks {
    private static Object api;
    private static Method inUse;
    private static boolean looked;

    private ShaderPacks() {}

    public static boolean inUse() {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void inCameraView(WorldRenderContext context, Runnable draw) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(context.projectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        modelView.pushMatrix();
        modelView.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            draw.run();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}

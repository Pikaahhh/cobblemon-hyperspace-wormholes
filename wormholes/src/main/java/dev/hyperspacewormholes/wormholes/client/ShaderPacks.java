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
    private static Method inUse, shadowPass, manager, pipelineOf, getPhase, setPhase;
    private static Object none;
    private static boolean looked;

    private ShaderPacks() {}

    /** True while Iris draws its shadow pass (the world from the sun), when an entity renderer is called a second time. */
    public static boolean inShadowPass() {
        if (!inUse()) return false;
        try {
            return (boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * Runs a sky draw as if it weren't in the sky: Iris gives textured sky draws the pack's sky program, and under a pack
     * like BSL the warp ride's nebula and the Megalopolis planets came out dark. Outside the sky phase they get its plain
     * textured program. Through Iris's internals with reflection; if they change, the draw just runs as is.
     * Draw the sky shrunk by skyScale() in there too, or the pack fogs it over as if it were far away.
     */
    public static void outsideSky(Runnable draw) {
        Object pipeline = null, phase = null;
        if (inUse()) {
            try {
                if (setPhase == null) {
                    Class<?> phases = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
                    Class<?> type = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
                    manager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager");
                    pipelineOf = Class.forName("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable");
                    getPhase = type.getMethod("getPhase");
                    none = phases.getField("NONE").get(null);
                    setPhase = type.getMethod("setPhase", phases);
                }
                pipeline = pipelineOf.invoke(manager.invoke(null));
                if (pipeline != null) {
                    phase = getPhase.invoke(pipeline);
                    setPhase.invoke(pipeline, none);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                pipeline = null;
            }
        }
        try {
            draw.run();
        } finally {
            if (pipeline != null && phase != null) {
                try {
                    setPhase.invoke(pipeline, phase);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
    }

    /** What to shrink a sky drawn in outsideSky by: it looks the same from the camera, but the pack's fog sees it close by. */
    public static float skyScale() {
        return inUse() ? 0.1f : 1f;
    }

    public static boolean inUse() {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
                shadowPass = type.getMethod("isRenderingShadowPass");
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

package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4fStack;

import java.util.function.Consumer;

/**
 * The points of a frame the wormholes half draws at. start comes before the terrain, beforeEntities once the solid and
 * cutout terrain is down, afterEntities right after the entities, afterTranslucent after water and particles (into the
 * main target, Fabulous too) and last once the whole world is drawn, still in the camera's view and the terrain's fog.
 */
public final class RenderStages {
    private static float fogStart;
    private static boolean keepingFog;

    private RenderStages() {}

    public static void start(Consumer<RenderLevelStageEvent> draw) {
        at(RenderLevelStageEvent.Stage.AFTER_SKY, draw);
    }

    public static void beforeEntities(Consumer<RenderLevelStageEvent> draw) {
        at(RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, draw);
    }

    public static void afterEntities(Consumer<RenderLevelStageEvent> draw) {
        at(RenderLevelStageEvent.Stage.AFTER_ENTITIES, draw);
    }

    public static void afterTranslucent(Consumer<RenderLevelStageEvent> draw) {
        at(RenderLevelStageEvent.Stage.AFTER_PARTICLES, event -> {
            if (!Minecraft.useShaderTransparency()) {
                draw.accept(event);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            mc.getMainRenderTarget().bindWrite(false);
            draw.accept(event);
            mc.levelRenderer.getParticlesTarget().bindWrite(false);
        });
    }

    public static void last(Consumer<RenderLevelStageEvent> draw) {
        if (!keepingFog) {
            keepingFog = true;
            at(RenderLevelStageEvent.Stage.AFTER_WEATHER, event -> fogStart = RenderSystem.getShaderFogStart());
        }
        at(RenderLevelStageEvent.Stage.AFTER_LEVEL, event -> {
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.mul(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(fogStart);
            try {
                draw.accept(event);
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                FogRenderer.setupNoFog();
            }
        });
    }

    private static void at(RenderLevelStageEvent.Stage stage, Consumer<RenderLevelStageEvent> draw) {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == stage) draw.accept(event);
        });
    }
}

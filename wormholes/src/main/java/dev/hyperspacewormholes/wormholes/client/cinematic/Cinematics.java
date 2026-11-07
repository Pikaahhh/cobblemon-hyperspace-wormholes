package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * One place for the cinematic mixins to ask about whichever of Necrozma's cutscenes is on: the Ultra Burst
 * (UltraBurstCinematic) or the power-up and defeat scenes (NecrozmaSceneCinematic). Each hides the HUD and battle UI,
 * holds the player still, drives the camera and hides what its rules hide; the NecrozmaScenePayload receiver lives here
 * too.
 */
public final class Cinematics {
    private Cinematics() {}

    public static void register() {
        UltraBurstCinematic.register();
        NecrozmaSceneCinematic.register();
        UltraAura.register();
    }

    public static void receive(NecrozmaScenePayload payload) {
        NecrozmaSceneCinematic.play(payload);
    }

    public static boolean isPlaying() {
        return UltraBurstCinematic.isPlaying() || NecrozmaSceneCinematic.isPlaying();
    }

    public static boolean isShowing() {
        return UltraBurstCinematic.isShowing() || NecrozmaSceneCinematic.isShowing();
    }

    public static boolean isBoss(Entity entity) {
        return UltraBurstCinematic.isBoss(entity) || NecrozmaSceneCinematic.isBoss(entity);
    }

    public static @Nullable double[] camera(float partialTick) {
        double[] view = UltraBurstCinematic.camera(partialTick);
        return view != null ? view : NecrozmaSceneCinematic.camera(partialTick);
    }

    public static boolean hides(Entity entity) {
        return UltraBurstCinematic.hides(entity) || NecrozmaSceneCinematic.hides(entity);
    }

    public static boolean hud(GuiGraphics graphics, DeltaTracker delta) {
        boolean burst = UltraBurstCinematic.hud(graphics, delta);
        boolean scene = NecrozmaSceneCinematic.hud(graphics, delta);
        return burst || scene;
    }

    public static void trackModel(Entity entity, PosableModel model, PosableState state) {
        if (UltraBurstCinematic.isPlaying()) UltraBurstCinematic.trackDome(entity, model, state);
        UltraAura.track(entity, model, state);
    }
}

package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * One place for the cinematic mixins to ask about whichever of Necrozma's cutscenes is on: the Ultra Burst
 * (UltraBurstCinematic) or the power-up and defeat scenes (NecrozmaSceneCinematic). Each hides the HUD and battle UI,
 * holds the player still, drives the camera and hides what its rules hide; the NecrozmaScenePayload receiver lives here
 * too. With -Dhorizons.autotest=true, /necrozmascene power_up|defeat and /necrozmascene seek <frame> play a scene on the
 * nearest Necrozma.
 */
public final class Cinematics {
    private Cinematics() {}

    public static void register() {
        UltraBurstCinematic.register();
        NecrozmaSceneCinematic.register();
        UltraAura.register();
        if (Boolean.getBoolean("horizons.autotest")) {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                    ClientCommandManager.literal("necrozmascene")
                            .then(ClientCommandManager.literal("seek").then(ClientCommandManager.argument("frame", DoubleArgumentType.doubleArg(0))
                                    .executes(c -> NecrozmaSceneCinematic.seek(DoubleArgumentType.getDouble(c, "frame")) ? 1 : 0)))
                            .then(ClientCommandManager.argument("kind", StringArgumentType.word())
                                    .executes(c -> testScene(StringArgumentType.getString(c, "kind"))))));
        }
    }

    private static int testScene(String kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        PokemonEntity nearest = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PokemonEntity p && p.getPokemon().getSpecies().getName().equalsIgnoreCase("necrozma")
                    && (nearest == null || p.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) nearest = p;
        }
        if (nearest == null) return 0;
        int k = kind.equals("defeat") ? NecrozmaScenePayload.DEFEAT : NecrozmaScenePayload.POWER_UP;
        return NecrozmaSceneCinematic.play(new NecrozmaScenePayload(k, nearest.getId(),
                k == NecrozmaScenePayload.DEFEAT ? NecrozmaSceneCinematic.DEFEAT_TICKS : NecrozmaSceneCinematic.POWER_UP_TICKS)) ? 1 : 0;
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

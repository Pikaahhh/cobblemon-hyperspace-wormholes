package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import kotlin.Unit;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Action effect keyframe {@value #TYPE}: plays its "keyframes" (a move's stand-in Cobblemon or vanilla sounds) in
 * order, but only when the jar has no extra move sounds; with them (UsumSounds.available()) the
 * move's horizons_movefx:usum_sound cues take over and this is skipped. Example: {"type": "horizons_movefx:stand_in",
 * "keyframes": [{"type": "entity_sound", "sound": "cobblemon:impact.flying"}]}.
 */
public class StandInKeyframe implements ActionEffectKeyframe {
    public static final String TYPE = "horizons_movefx:stand_in";

    public List<ActionEffectKeyframe> keyframes = List.of();

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        if (UsumSounds.available() || keyframes == null || keyframes.isEmpty()) {
            return CompletableFuture.completedFuture(Unit.INSTANCE);
        }
        CompletableFuture<Unit> done = new CompletableFuture<>();
        context.getActionEffect().chainKeyframes(context, keyframes.iterator(), done);
        return done;
    }
}

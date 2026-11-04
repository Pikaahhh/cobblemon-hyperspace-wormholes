package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import kotlin.Unit;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Action effect keyframe {@value #TYPE}: the cameras of players near the battle shake for "duration" seconds, starting
 * "delay" seconds after the keyframe, with a strength going from "from" to "to" and a speed "rate" (Ultra Sun's
 * CAMERA_SHAKE numbers, which the clients scale down to a gentle turn). The animation goes straight on.
 * Example: {"type": "horizons_movefx:camera_shake", "delay": 3.3, "duration": 0.77, "from": 8, "to": 5, "rate": 15}.
 */
public class CameraShakeKeyframe implements ActionEffectKeyframe {
    public static final String TYPE = "horizons_movefx:camera_shake";

    public float delay = 0;
    public float duration = 0.5f;
    public float from = 5;
    public float to = 5;
    public float rate = 10;

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        List<Entity> battlers = MoveFx.battlers(context);
        if (!battlers.isEmpty() && duration > 0) {
            MoveFx.sendNear(battlers, new CameraShakePayload(MoveFx.centre(battlers), Math.max(0, delay), duration,
                    Math.max(0, from), Math.max(0, to), Math.max(0, rate)));
        }
        return CompletableFuture.completedFuture(Unit.INSTANCE);
    }
}

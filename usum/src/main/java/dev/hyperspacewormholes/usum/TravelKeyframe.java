package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.api.scheduling.SchedulingFunctionsKt;
import kotlin.Unit;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * Action effect keyframe {@value #TYPE}: waits while a projectile or beam front crosses the real gap between the user
 * and its target, so the impact keyframes after it fire on arrival: the centre-to-centre distance divided by "speed"
 * (blocks a second), kept between "min" and "max" seconds, plus "add" seconds; "fallback" seconds when there is no
 * target. Example: {"type": "horizons_movefx:travel", "speed": 14, "min": 0.1, "max": 2.5}.
 */
public class TravelKeyframe implements ActionEffectKeyframe {
    public static final String TYPE = "horizons_movefx:travel";

    public float speed = 12;
    public float min = 0.1f;
    public float max = 2.5f;
    public float add = 0;
    public float fallback = 0.4f;

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        Entity user = null;
        Entity target = null;
        for (Object provider : context.getProviders()) {
            if (provider instanceof UsersProvider users && user == null && !users.getEntities().isEmpty()) {
                user = users.getEntities().getFirst();
            } else if (provider instanceof TargetsProvider targets && target == null && !targets.getEntities().isEmpty()) {
                target = targets.getEntities().getFirst();
            }
        }
        float seconds = fallback;
        if (user != null && target != null && user != target && user.level() == target.level() && speed > 0) {
            double gap = centre(user).distanceTo(centre(target));
            seconds = Mth.clamp((float) (gap / speed), min, max);
        }
        return SchedulingFunctionsKt.delayedFuture(Math.max(0, seconds + add));
    }

    private static Vec3 centre(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2, 0);
    }
}

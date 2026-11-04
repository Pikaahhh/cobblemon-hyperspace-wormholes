package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.EntityProvider;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import kotlin.Unit;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Action effect keyframe {@value #TYPE}: moves the drawn model of the user ("who": "user") or of the targets ("target") along a curve, starting "delay" seconds after the keyframe, without moving the entity itself (render only, client.ModelMotion).
 * Each of "keys" is [seconds, forward, up, side, pitch, yaw, roll, scale side, scale up, scale forward, reach]: blocks along the Pokemon's facing, up and to its right, degrees about its body centre (pitch nose up, yaw to its left, roll to its right), the model's scale along its side, up and facing axes about its feet (1 as it is, 0 hidden), and reach, a share of the real distance to the Pokemon on the other side added to forward so a spot given next to the target lands on the real target at any battle spacing.
 * Values run in straight lines between keys and the model is back where it stands after the last. Example: {"type": "horizons_movefx:model_motion", "who": "user", "keys": [[0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0], [0.3, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1], [0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]]}.
 */
public class ModelMotionKeyframe implements ActionEffectKeyframe {
    public static final String TYPE = "horizons_movefx:model_motion";

    public String who = "user";
    public float delay = 0;
    public float[][] keys = new float[0][];

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        List<Entity> users = new ArrayList<>(), targets = new ArrayList<>();
        for (Object provider : context.getProviders()) {
            if (provider instanceof TargetsProvider) targets.addAll(((EntityProvider) provider).getEntities());
            else if (provider instanceof UsersProvider) users.addAll(((EntityProvider) provider).getEntities());
        }
        boolean moveTargets = "target".equals(who);
        List<Entity> movers = moveTargets ? targets : users;
        if (movers.isEmpty() || keys.length == 0) return CompletableFuture.completedFuture(Unit.INSTANCE);
        List<Entity> battlers = MoveFx.battlers(context);
        for (Entity mover : movers) {
            List<Entity> others = moveTargets ? users : targets;
            Entity other = others.isEmpty() ? null : others.getFirst();
            double distance = other == null || other == mover || other.level() != mover.level() ? 0
                    : Math.hypot(other.getX() - mover.getX(), other.getZ() - mover.getZ());
            List<Float> flat = new ArrayList<>();
            for (float[] key : keys) {
                if (key == null || key.length == 0) continue;
                float reach = key.length > ModelMotionPayload.STRIDE ? key[ModelMotionPayload.STRIDE] : 0f;
                for (int i = 0; i < ModelMotionPayload.STRIDE; i++) {
                    float value = i < key.length ? key[i] : i >= 7 ? 1f : 0f;
                    if (i == 1) value += (float) (reach * distance);
                    flat.add(value);
                }
            }
            if (!flat.isEmpty()) {
                MoveFx.sendNear(battlers, new ModelMotionPayload(mover.getId(), Math.max(0, delay), flat));
            }
        }
        return CompletableFuture.completedFuture(Unit.INSTANCE);
    }
}

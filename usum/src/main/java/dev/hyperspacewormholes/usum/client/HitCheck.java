package dev.hyperspacewormholes.usum.client;

import com.cobblemon.mod.common.client.particle.ParticleStorm;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.bedrockk.molang.runtime.value.DoubleValue;
import com.cobblemon.mod.common.entity.PosableEntity;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps move effects on the Pokemon's bodies. ensureBodyLocators gives a model without a "middle" or "target" locator one
 * at the centre of its hitbox (its root locator raised by half the hitbox height, or just under "head" when that is
 * higher, so a hovering Pokemon such as Mew gets it on its body), so effects aimed there reach the body instead of
 * Cobblemon's fallback, the feet. prepare gives every effect v.emitter_scale, which travel() needs to turn
 * v.target_distance into blocks.
 */
public final class HitCheck {
    private static final Set<MatrixWrapper> MADE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<String> BODY = List.of("middle", "target");

    private HitCheck() {
    }

    public static void ensureBodyLocators(Entity entity) {
        if (!(entity instanceof PosableEntity posable) || !(posable.getDelegate() instanceof PosableState state)) return;
        Map<String, MatrixWrapper> locators = state.getLocatorStates();
        MatrixWrapper root = locators.get("root");
        if (root == null) return;
        MatrixWrapper head = locators.get("head");
        for (String name : BODY) {
            MatrixWrapper have = locators.get(name);
            if (have != null && !MADE.contains(have)) continue;
            Matrix4f matrix = new Matrix4f(root.getMatrix());
            Vector3f at = matrix.getTranslation(new Vector3f());
            float y = at.y + entity.getBbHeight() / 2f;
            if (head != null && !MADE.contains(head)) {
                y = Math.max(y, head.getMatrix().getTranslation(new Vector3f()).y - 0.35f * entity.getBbHeight());
            }
            matrix.setTranslation(at.x, y, at.z);
            MatrixWrapper made = have != null ? have : new MatrixWrapper();
            made.updateMatrix(matrix);
            made.updatePosition(root.getPosition());
            if (have == null) {
                try {
                    locators.put(name, made);
                    MADE.add(made);
                } catch (UnsupportedOperationException ignored) {
                    return;
                }
            }
        }
    }

    public static void prepare(ParticleStorm storm) {
        Vector3f scale = storm.getEmitterSpaceMatrix().getMatrix().getScale(new Vector3f());
        storm.getRuntime().getEnvironment().setSimpleVariable("emitter_scale", new DoubleValue((double) scale.x));
    }

}

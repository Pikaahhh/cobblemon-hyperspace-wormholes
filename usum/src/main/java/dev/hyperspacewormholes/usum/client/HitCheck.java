package dev.hyperspacewormholes.usum.client;

import com.cobblemon.mod.common.client.particle.ParticleStorm;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.bedrockk.molang.runtime.value.DoubleValue;
import com.cobblemon.mod.common.entity.PosableEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps move effects on the Pokemon's bodies. ensureBodyLocators gives a model without a "middle" or "target" locator one
 * at the centre of its hitbox (its root locator raised by half the hitbox height, or just under "head" when that is
 * higher, so a hovering Pokemon such as Mew gets it on its body), so effects aimed there reach the body instead of
 * Cobblemon's fallback, the feet. prepare gives every effect v.emitter_scale, which travel() needs to turn
 * v.target_distance into blocks. With -Dhorizons_movefx.hitcheck=true, log writes a "[movefx-hit]" line per effect.
 */
public final class HitCheck {
    private static final Logger LOG = LoggerFactory.getLogger("horizons_movefx");
    private static final boolean ENABLED = Boolean.getBoolean("horizons_movefx.hitcheck");
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

    public static void log(ParticleStorm storm) {
        if (!ENABLED) return;
        ResourceLocation id = storm.getEffect().getId();
        if (id == null || !"horizons_movefx".equals(id.getNamespace())) return;
        Entity source = storm.getEntity();
        Vec3 emitter = storm.getEmitterSpaceMatrix().getOrigin();
        StringBuilder line = new StringBuilder("[movefx-hit] effect=").append(id.getPath());
        line.append(" emitter=").append(vec(emitter));
        if (source != null) {
            line.append(" src=").append(source.getId()).append(':').append(source.getName().getString().replace(' ', '_'));
            line.append(" box=").append(box(source.getBoundingBox()));
        }
        Vec3 aim = null;
        try {
            aim = storm.getTargetPos().invoke();
        } catch (RuntimeException ignored) {
        }
        if (aim != null && !aim.equals(Vec3.ZERO) && source != null) {
            line.append(" aim=").append(vec(aim));
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LivingEntity other : source.level().getEntitiesOfClass(LivingEntity.class, new AABB(aim, aim).inflate(16), e -> e != source)) {
                double d = Math.sqrt(other.getBoundingBox().distanceToSqr(aim));
                if (d < bestDistance) {
                    bestDistance = d;
                    best = other;
                }
            }
            if (best != null) {
                line.append(" tgt=").append(best.getId()).append(':').append(best.getName().getString().replace(' ', '_'));
                line.append(" tbox=").append(box(best.getBoundingBox()));
            }
        }
        LOG.info(line.toString());
    }

    private static String vec(Vec3 v) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", v.x, v.y, v.z);
    }

    private static String box(AABB b) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
}

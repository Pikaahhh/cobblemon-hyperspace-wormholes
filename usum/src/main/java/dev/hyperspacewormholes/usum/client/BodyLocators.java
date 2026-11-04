package dev.hyperspacewormholes.usum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Locators that follow a battling Pokemon's animated body, for move effects that must sit on it.
 * Cobblemon's own locators can't see a primary animation's other bones or the drawn-only model motion (client.ModelMotion), so after they update this adds movefx_chest, movefx_centre and movefx_head (centre of the named bones' cubes) and movefx_rim (centre of the best-fit circle through the wing bones' pivots), each run through the model motion.
 * A model without the bones gets a locator at a share of its hitbox height. Only used for Pokemon in a battle.
 */
public final class BodyLocators {
    public enum Kind { CENTROID, RING }

    public record Spec(String name, Kind kind, List<String> bones, float fallbackHeight) {}

    public static final List<Spec> SPECS = List.of(
            new Spec("movefx_chest", Kind.CENTROID, List.of("chest"), 0.55f),
            new Spec("movefx_centre", Kind.CENTROID, List.of("chest", "torso_lower"), 0.5f),
            new Spec("movefx_head", Kind.CENTROID, List.of("head"), 0.85f),
            new Spec("movefx_rim", Kind.RING, List.of("finger_left4", "finger_left8", "outer_wing_arch_left_rotaton",
                    "membrane_lower_left2", "finger_right4", "finger_right8", "outer_wing_arch_right_rotation",
                    "membrane_lower_right2"), 0.6f));

    /** Points in one part's own space (pixels), reached from the root joint through path. */
    private record Points(List<ModelPart> path, List<Vector3f> local) {}

    private static final Map<ModelPart, List<List<Points>>> RESOLVED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float PI = (float) Math.PI;

    private BodyLocators() {
    }

    /**
     * The body locators' matrices (relative to the entity's feet, world axes, as Cobblemon's own) for a posed model.
     * joint is the model's root joint, rootLocator Cobblemon's root locator matrix this frame, motion the model motion's matrix or null, bbHeight the hitbox height for the fallback spots.
     */
    public static Map<String, Matrix4f> compute(ModelPart joint, Matrix4fc rootLocator, @Nullable Matrix4fc motion,
                                                float bbHeight) {
        List<List<Points>> resolved = RESOLVED.computeIfAbsent(joint, BodyLocators::resolve);
        Matrix4f base = new Matrix4f(rootLocator).rotateZ(PI);
        Matrix4f lin = new Matrix4f(rootLocator).setTranslation(0, 0, 0);
        Vector3f origin = rootLocator.getTranslation(new Vector3f());
        float scale = lin.getScale(new Vector3f()).x;
        Vector3f front = lin.transformDirection(new Vector3f(0, 0, -1)).normalize();
        Map<String, Matrix4f> out = new LinkedHashMap<>();
        for (int i = 0; i < SPECS.size(); i++) {
            Spec spec = SPECS.get(i);
            List<Vector3f> pts = world(base, resolved.get(i));
            Matrix4f m;
            if (pts.isEmpty()) {
                m = new Matrix4f().translation(origin.x, origin.y + spec.fallbackHeight() * bbHeight, origin.z).mul(lin);
            } else if (spec.kind() == Kind.CENTROID || pts.size() < 3) {
                Vector3f c = mean(pts);
                m = new Matrix4f().translation(c).mul(lin);
            } else {
                m = ring(pts, front, scale);
            }
            out.put(spec.name(), motion == null ? m : new Matrix4f(motion).mul(m));
        }
        return out;
    }

    private static List<Vector3f> world(Matrix4f base, List<Points> sets) {
        List<Vector3f> out = new ArrayList<>();
        for (Points set : sets) {
            PoseStack stack = new PoseStack();
            stack.last().pose().set(base);
            for (ModelPart part : set.path()) part.translateAndRotate(stack);
            Matrix4f at = stack.last().pose();
            for (Vector3f p : set.local()) out.add(at.transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f()));
        }
        return out;
    }

    private static Vector3f mean(List<Vector3f> pts) {
        Vector3f c = new Vector3f();
        for (Vector3f p : pts) c.add(p);
        return c.div(pts.size());
    }

    /** The ring's frame: best-fit plane (smallest spread), best-fit circle in it, normal to the front, up in it. */
    private static Matrix4f ring(List<Vector3f> pts, Vector3f front, float scale) {
        Vector3f c = mean(pts);
        float[][] cov = new float[3][3];
        for (Vector3f p : pts) {
            float[] d = {p.x - c.x, p.y - c.y, p.z - c.z};
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) cov[a][b] += d[a] * d[b];
        }
        Vector3f n = smallestEigenvector(cov);
        if (n.dot(front) < 0) n.negate();
        Vector3f e1 = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0).cross(n).normalize() : new Vector3f(1, 0, 0).cross(n).normalize();
        Vector3f e2 = new Vector3f(n).cross(e1);
        // least-squares circle: 2u a + 2v b + k = u^2 + v^2 in the plane's (e1, e2) coordinates
        Matrix3f ata = new Matrix3f().zero();
        Vector3f atb = new Vector3f();
        for (Vector3f p : pts) {
            Vector3f d = new Vector3f(p).sub(c);
            float u = d.dot(e1), v = d.dot(e2);
            float[] row = {2 * u, 2 * v, 1};
            float rhs = u * u + v * v;
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) ata.setRowColumn(a, b, ata.getRowColumn(a, b) + row[a] * row[b]);
            }
            atb.add(row[0] * rhs, row[1] * rhs, row[2] * rhs);
        }
        Vector3f centre = new Vector3f(c);
        if (Math.abs(ata.determinant()) > 1e-9f) {
            Vector3f sol = ata.invert().transform(atb);
            centre.add(new Vector3f(e1).mul(sol.x)).add(new Vector3f(e2).mul(sol.y));
        }
        Vector3f x = new Vector3f(0, 1, 0).cross(n);
        if (x.lengthSquared() < 1e-6f) x.set(e1);
        x.normalize();
        Vector3f y = new Vector3f(n).cross(x);
        return new Matrix4f(x.x * scale, x.y * scale, x.z * scale, 0,
                y.x * scale, y.y * scale, y.z * scale, 0,
                n.x * scale, n.y * scale, n.z * scale, 0,
                centre.x, centre.y, centre.z, 1);
    }

    /** Unit eigenvector of a symmetric 3x3 matrix's smallest eigenvalue (cyclic Jacobi). */
    static Vector3f smallestEigenvector(float[][] m) {
        double[][] a = new double[3][3], v = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) a[i][j] = m[i][j];
        for (int sweep = 0; sweep < 32; sweep++) {
            double off = Math.abs(a[0][1]) + Math.abs(a[0][2]) + Math.abs(a[1][2]);
            if (off < 1e-12) break;
            for (int p = 0; p < 2; p++) {
                for (int q = p + 1; q < 3; q++) {
                    if (Math.abs(a[p][q]) < 1e-15) continue;
                    double theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
                    double t = Math.signum(theta == 0 ? 1 : theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    double cs = 1 / Math.sqrt(t * t + 1), sn = t * cs;
                    for (int k = 0; k < 3; k++) {
                        double akp = a[k][p], akq = a[k][q];
                        a[k][p] = cs * akp - sn * akq;
                        a[k][q] = sn * akp + cs * akq;
                    }
                    for (int k = 0; k < 3; k++) {
                        double apk = a[p][k], aqk = a[q][k];
                        a[p][k] = cs * apk - sn * aqk;
                        a[q][k] = sn * apk + cs * aqk;
                    }
                    for (int k = 0; k < 3; k++) {
                        double vkp = v[k][p], vkq = v[k][q];
                        v[k][p] = cs * vkp - sn * vkq;
                        v[k][q] = sn * vkp + cs * vkq;
                    }
                }
            }
        }
        int best = 0;
        for (int i = 1; i < 3; i++) if (a[i][i] < a[best][best]) best = i;
        return new Vector3f((float) v[0][best], (float) v[1][best], (float) v[2][best]).normalize();
    }

    /** Per spec, the point sets of the bones it names that the model has (empty: use the fallback spot). */
    private static List<List<Points>> resolve(ModelPart joint) {
        Map<String, List<ModelPart>> paths = new LinkedHashMap<>();
        walk(joint, new ArrayList<>(), paths);
        List<List<Points>> out = new ArrayList<>();
        for (Spec spec : SPECS) {
            List<Points> sets = new ArrayList<>();
            for (String bone : spec.bones()) {
                List<ModelPart> path = paths.get(bone);
                if (path == null) continue;
                List<Vector3f> local = new ArrayList<>();
                if (spec.kind() == Kind.RING) {
                    local.add(new Vector3f());
                } else {
                    for (ModelPart.Cube cube : path.get(path.size() - 1).cubes) {
                        local.add(new Vector3f((cube.minX + cube.maxX) / 2, (cube.minY + cube.maxY) / 2,
                                (cube.minZ + cube.maxZ) / 2));
                    }
                }
                if (!local.isEmpty()) sets.add(new Points(List.copyOf(path), local));
            }
            out.add(sets);
        }
        return out;
    }

    /** ModelPart's children and cubes are public through Cobblemon's access widener. */
    private static void walk(ModelPart part, List<ModelPart> path, Map<String, List<ModelPart>> out) {
        for (Map.Entry<String, ModelPart> e : part.children.entrySet()) {
            List<ModelPart> next = new ArrayList<>(path);
            next.add(e.getValue());
            out.putIfAbsent(e.getKey(), next);
            walk(e.getValue(), next, out);
        }
    }
}

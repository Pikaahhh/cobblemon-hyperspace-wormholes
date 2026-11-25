package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.render.models.blockbench.LocatorAccess;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seats a Solgaleo model lacks. Mega Showdown's has seat_1 only, and Cobblemon leaves a rider whose seat has no
 * locator where they got on, so a missing seat_2, seat_3 or seat_4 is added to the model's locators as it is made
 * (VaryingRenderableResolverMixin), in line behind seat_1: seat_2 on seat_1's own bone, seat_3 and seat_4 on the
 * haunches' so they follow their animation. Each Spot is from seat_1 with the model at rest, in model pixels. A seat
 * the model has is left as it is, and so is a model without seat_1.
 */
public final class SolgaleoSeats {
    private record Spot(String seat, boolean haunches, float up, float back) {}

    private static final String SPECIES = "cobblemon:solgaleo", FRONT = "seat_1", HAUNCHES = "waist";
    private static final List<Spot> SPOTS = List.of(new Spot("seat_2", false, 0f, 8.5f),
            new Spot("seat_3", true, -3.25f, 18.5f), new Spot("seat_4", true, -3f, 26.5f));
    private static boolean told;

    private SolgaleoSeats() {}

    public static void add(String name, PosableModel model) {
        if (!SPECIES.equals(name)) return;
        try {
            Bone root = model.getLocatorAccess().getJoint();
            Map<Bone, Matrix4f> rest = new IdentityHashMap<>();
            Map<Bone, Bone> parents = new IdentityHashMap<>();
            Map<String, Bone> bones = new HashMap<>();
            walk(root, new Matrix4f(), rest, parents, bones);
            Bone front = bones.get(LocatorAccess.PREFIX + FRONT);
            if (front == null) {
                tell("has no " + FRONT, null);
                return;
            }
            Bone own = parents.get(front);
            Map<Bone, Map<String, Bone>> extra = new IdentityHashMap<>();
            for (Spot spot : SPOTS) {
                if (bones.containsKey(LocatorAccess.PREFIX + spot.seat)) continue;
                Vector3f at = rest.get(front).getTranslation(new Vector3f()).add(0f, -spot.up, spot.back);
                Bone joint = spot.haunches ? bones.getOrDefault(HAUNCHES, own) : own;
                new Matrix4f(rest.get(joint)).invert().transformPosition(at);
                ModelPart part = new ModelPart(List.of(), Map.of());
                part.setInitialPose(PartPose.offset(at.x, at.y, at.z));
                part.resetPose();
                extra.computeIfAbsent(joint, j -> new LinkedHashMap<>()).put(spot.seat, (Bone) (Object) part);
            }
            if (!extra.isEmpty()) model.setLocatorAccess(resolve(root, extra));
        } catch (RuntimeException e) {
            tell("couldn't be given its passenger seats", e);
        }
    }

    private static void walk(Bone bone, Matrix4f at, Map<Bone, Matrix4f> rest, Map<Bone, Bone> parents, Map<String, Bone> bones) {
        rest.put(bone, at);
        bone.getChildren().forEach((name, child) -> {
            PartPose pose = ((ModelPart) (Object) child).getInitialPose();
            bones.putIfAbsent(name, child);
            parents.put(child, bone);
            walk(child, new Matrix4f(at).translate(pose.x, pose.y, pose.z)
                    .rotateZYX(pose.zRot, pose.yRot, pose.xRot), rest, parents, bones);
        });
    }

    /** LocatorAccess.resolve, with the extra seats on their joints. */
    private static LocatorAccess resolve(Bone bone, Map<Bone, Map<String, Bone>> extra) {
        Map<String, Bone> locators = new LinkedHashMap<>();
        List<LocatorAccess> children = new ArrayList<>();
        bone.getChildren().forEach((name, child) -> {
            if (name.startsWith(LocatorAccess.PREFIX)) {
                locators.put(name.substring(LocatorAccess.PREFIX.length()), child);
            } else {
                LocatorAccess access = resolve(child, extra);
                if (access != null) children.add(access);
            }
        });
        locators.putAll(extra.getOrDefault(bone, Map.of()));
        return children.isEmpty() && locators.isEmpty() ? null : new LocatorAccess(bone, locators, children);
    }

    private static void tell(String why, RuntimeException error) {
        if (told) return;
        told = true;
        Wormholes.LOGGER.warn("[solgaleo seats] the loaded Solgaleo model {}; it stays as it is", why, error);
    }
}

package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/** Finds where a posed Pokemon model's bones are in the world, for effects that come out of a mouth. */
public final class Bones {
    private Bones() {}

    public static @Nullable Vec3 world(Entity entity, PosableModel model, PosableState state, String name) {
        ModelPart part = model.getRelevantPartsByName().get(name);
        MatrixWrapper root = state.getLocatorStates().get("root");
        if (part == null || root == null) return null;
        Vector3f at = origin(model.getRootPart(), part, root.getMatrix());
        if (at == null) return null;
        return entity.getPosition(state.getPartialTicks()).add(at.x, at.y, at.z);
    }

    private static @Nullable Vector3f origin(Bone root, ModelPart target, Matrix4f rootLocator) {
        Deque<Bone> path = new ArrayDeque<>();
        if (!pathTo(root, target, path)) return null;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(rootLocator).rotateZ(-180f * Mth.DEG_TO_RAD);
        for (Bone bone : path) bone.transform(pose);
        return pose.last().pose().getTranslation(new Vector3f());
    }

    private static boolean pathTo(Bone from, Object target, Deque<Bone> path) {
        for (Bone child : from.getChildren().values()) {
            path.addLast(child);
            if (child == target || pathTo(child, target, path)) return true;
            path.removeLast();
        }
        return false;
    }
}

package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon's pathfinder thinks every Pokemon is one block wide, so big ones walk into gaps they can't fit and get
 * stuck. For Pokemon we walk somewhere ourselves (quick-battle chases and herd followers, tagged
 * hyperspace_wormholes_fit_path), a step only counts if the whole hitbox fits, so they go around instead. Anything a
 * block wide or smaller is left alone. Uses intermediary names since that's how Cobblemon ships.
 */
@Mixin(value = OmniPathNodeMaker.class, remap = false)
public abstract class FitPathMixin extends NodeEvaluator {
    @Inject(method = "findAcceptedNodeWalk(IIIIDLnet/minecraft/class_2350;Lnet/minecraft/class_7;)Lnet/minecraft/class_9;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void hyperspace_wormholes$fits(int x, int y, int z, int step, double floor, Direction direction, PathType type, CallbackInfoReturnable<Node> cir) {
        Node node = cir.getReturnValue();
        if (node == null || mob == null || !mob.getTags().contains("hyperspace_wormholes_fit_path")) return;
        float width = mob.getBbWidth();
        if (width <= 1) return;
        double half = (width - 0.1) / 2, offset = (int) (width + 1) * 0.5;
        double cx = node.x + offset, cz = node.z + offset;
        AABB box = new AABB(cx - half, node.y + 0.55, cz - half, cx + half, node.y + Math.max(mob.getBbHeight(), 0.65), cz + half);
        if (!mob.level().noCollision(mob, box)) cir.setReturnValue(null);
    }
}

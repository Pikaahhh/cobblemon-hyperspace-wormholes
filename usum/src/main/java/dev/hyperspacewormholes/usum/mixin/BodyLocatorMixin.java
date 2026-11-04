package dev.hyperspacewormholes.usum.mixin;

import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.LocatorAccess;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.usum.client.BodyLocators;
import dev.hyperspacewormholes.usum.client.ModelMotion;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Right after Cobblemon updates a model's locators, adds the body locators that follow the animated bones and the model
 * motion (client.BodyLocators: movefx_chest, movefx_centre, movefx_head, movefx_rim) to the state's locators for a
 * Pokemon in a battle. Each keeps its MatrixWrapper from frame to frame, as a running particle effect holds on to it.
 * The bare method name keeps Minecraft types out of the selector, which differ between dev and the intermediary jar;
 * the mixin is optional (require 0), and without it effects fall back to the next locator they list.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class BodyLocatorMixin {
    @Inject(method = "updateLocators", at = @At("RETURN"), require = 0)
    private void horizons_movefx$bodyLocators(Entity entity, PosableState state, CallbackInfo ci) {
        if (!(entity instanceof PokemonEntity pokemon) || !pokemon.isBattling()) return;
        Map<String, MatrixWrapper> states = state.getLocatorStates();
        MatrixWrapper root = states.get("root");
        if (root == null) return;
        Object joint;
        try {
            LocatorAccess access = ((PosableModel) (Object) this).getLocatorAccess();
            joint = access == null ? null : access.getJoint();
        } catch (RuntimeException notReady) {
            return;
        }
        if (!(joint instanceof ModelPart part)) return;
        Matrix4f motion = new Matrix4f();
        boolean moved = ModelMotion.matrix(pokemon, state.getPartialTicks(), motion);
        Map<String, Matrix4f> made = BodyLocators.compute(part, root.getMatrix(), moved ? motion : null,
                entity.getBbHeight());
        for (Map.Entry<String, Matrix4f> e : made.entrySet()) {
            MatrixWrapper wrapper = states.get(e.getKey());
            if (wrapper == null) {
                wrapper = new MatrixWrapper();
                try {
                    states.put(e.getKey(), wrapper);
                } catch (UnsupportedOperationException fixed) {
                    return;
                }
            }
            wrapper.updateMatrix(e.getValue());
            wrapper.updatePosition(root.getPosition());
        }
    }
}

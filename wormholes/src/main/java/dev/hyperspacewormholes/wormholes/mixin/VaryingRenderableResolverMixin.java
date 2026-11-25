package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.wormholes.client.SolgaleoSeats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives a Solgaleo model the passenger seats it lacks as Cobblemon makes it (client.SolgaleoSeats). Optional (require 0):
 * without it the model keeps its own seats.
 */
@Mixin(value = VaryingRenderableResolver.class, remap = false)
public abstract class VaryingRenderableResolverMixin {
    @WrapOperation(method = "getPoser", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableModel;initializeLocatorAccess()V"))
    private void hyperspace_wormholes$solgaleoSeats(PosableModel model, Operation<Void> original) {
        original.call(model);
        SolgaleoSeats.add(((VaryingRenderableResolver) (Object) this).getName().toString(), model);
    }
}

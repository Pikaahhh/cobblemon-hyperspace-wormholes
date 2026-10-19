package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.NoBlockBreaking;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No placing blocks in Hyperspace.
 */
@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noPlacing(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (NoBlockBreaking.isHyperspace(context.getLevel())) cir.setReturnValue(InteractionResult.FAIL);
    }
}

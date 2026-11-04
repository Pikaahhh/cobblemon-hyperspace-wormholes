package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraNoBuilding;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No placing blocks in the Ultra Space dimensions but in creative (UltraNoBuilding). Runs on the client too, so it
 * never shows a block the server won't place; this catches the placements that don't come through UltraNoUseOnMixin (a
 * lily pad put on water, another mod's own placing).
 */
@Mixin(BlockItem.class)
public class UltraNoPlacingMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noUltraPlacing(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (UltraNoBuilding.blocks(context.getPlayer(), context.getLevel())) cir.setReturnValue(InteractionResult.FAIL);
    }
}

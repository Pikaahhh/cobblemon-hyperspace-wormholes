package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraNoBuilding;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the Ultra Space dimensions, except in creative (UltraNoBuilding), an item used on a block does nothing to it: nothing placed, lit, tilled, stripped or flattened, no bone meal. This is the item's part of a right-click on a block, after the block's own use (doors, buttons, the Item Analyser) has had its turn, so those still work.
 * It passes rather than fails so the right-click goes on to the item's ordinary use (a Poke Ball thrown at the ground still flies, a boat still goes on water). Runs on the client too, so the client never shows a change the server won't make.
 */
@Mixin(ItemStack.class)
public class UltraNoUseOnMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noUltraUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (UltraNoBuilding.blocks(context.getPlayer(), context.getLevel())) cir.setReturnValue(InteractionResult.PASS);
    }
}

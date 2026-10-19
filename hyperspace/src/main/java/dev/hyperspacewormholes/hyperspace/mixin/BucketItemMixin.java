package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.NoBlockBreaking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No buckets in Hyperspace.
 */
@Mixin(BucketItem.class)
public class BucketItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noBuckets(Level level, Player player, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (NoBlockBreaking.isHyperspace(level)) cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }
}

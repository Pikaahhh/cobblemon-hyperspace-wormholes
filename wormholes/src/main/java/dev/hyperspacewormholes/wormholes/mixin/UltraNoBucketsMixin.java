package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraNoBuilding;
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

/** No buckets in the Ultra Space dimensions but in creative (UltraNoBuilding); runs on the client too. */
@Mixin(BucketItem.class)
public class UltraNoBucketsMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noUltraBuckets(Level level, Player player, InteractionHand hand,
                                                     CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (UltraNoBuilding.blocks(player, level)) cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }
}

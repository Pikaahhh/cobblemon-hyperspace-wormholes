package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.OtherPlayersLegendaries;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes other people's hidden bosses unclickable.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hiddenBoss(CallbackInfoReturnable<Boolean> cir) {
        if (OtherPlayersLegendaries.hides((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}

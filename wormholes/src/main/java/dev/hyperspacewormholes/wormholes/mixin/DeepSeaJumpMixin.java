package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets you jump off the sea floor in the Ultra Deep Sea like you would on land.
 */
@Mixin(LivingEntity.class)
public abstract class DeepSeaJumpMixin {
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByFluids()Z"))
    private boolean hyperspace_wormholes$readJump(boolean affected) {
        return affected || hyperspace_wormholes$onSeaFloor();
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean hyperspace_wormholes$jumpFromGround(boolean inWater) {
        return inWater && !hyperspace_wormholes$onSeaFloor();
    }

    private boolean hyperspace_wormholes$onSeaFloor() {
        LivingEntity self = (LivingEntity) (Object) this;
        return self instanceof Player && UltraWarpRules.isUnderwater(self);
    }
}

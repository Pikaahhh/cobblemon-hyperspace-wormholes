package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraPlantLightning;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Ultra Plant in a dry thunderstorm: full thunder, dark sky, no rain.
 */
@Mixin(Level.class)
public abstract class UltraPlantWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$neverRaining(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (UltraPlantLightning.isUltraPlant((Level) (Object) this)) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$alwaysThundering(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (UltraPlantLightning.isUltraPlant((Level) (Object) this)) cir.setReturnValue(1f);
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noRealLightning(CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide && UltraPlantLightning.isUltraPlant(level)) cir.setReturnValue(false);
    }
}

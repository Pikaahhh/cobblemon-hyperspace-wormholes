package dev.hyperspacewormholes.usum.mixin;

import com.cobblemon.mod.common.client.particle.ParticleStorm;
import dev.hyperspacewormholes.usum.client.HitCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * As each particle effect starts, gives it v.emitter_scale (HitCheck.prepare). Optional (require 0).
 */
@Mixin(value = ParticleStorm.class, remap = false)
public abstract class HitCheckMixin {
    @Inject(method = "spawn()V", at = @At("HEAD"), require = 0)
    private void horizons_movefx$prepare(CallbackInfo ci) {
        HitCheck.prepare((ParticleStorm) (Object) this);
    }
}

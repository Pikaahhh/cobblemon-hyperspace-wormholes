package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.client.RideHush;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turns sounds down under the white of a ride (RideHush), those already playing and those that start in it.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private float hyperspace_wormholes$hush(float volume, SoundInstance sound) {
        return volume * RideHush.of(sound);
    }

    @ModifyExpressionValue(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float hyperspace_wormholes$hushNew(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * RideHush.of(sound);
    }
}

package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import dev.hyperspacewormholes.wormholes.client.UltraWorldMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Cobblemon's battle music for a normal battle in an Ultra world with a theme of its own, so the world's theme
 * carries on through it (UltraWorldMusic.keepsThemeOver). The mod's own battle music (a boss fight's) still plays, and
 * the theme holds off under it.
 */
@Mixin(value = BattleMusicController.class, remap = false)
public abstract class BattleMusicKeepThemeMixin {
    @Inject(method = "initializeMusic", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepTheme(BattleMusicInstance newMusic, CallbackInfo ci) {
        if (UltraWorldMusic.keepsThemeOver(newMusic)) ci.cancel();
    }
}

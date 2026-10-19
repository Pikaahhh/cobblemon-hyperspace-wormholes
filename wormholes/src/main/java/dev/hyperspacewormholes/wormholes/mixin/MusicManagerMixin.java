package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.UltraWormholeMusic;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla music quiet while the Ultra Wormhole theme is on.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$holdForWarpMusic(Music music, CallbackInfo ci) {
        if (UltraWormholeMusic.isPlaying()) ci.cancel();
    }
}

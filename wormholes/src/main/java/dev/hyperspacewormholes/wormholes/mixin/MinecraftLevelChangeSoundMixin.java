package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.wormholes.client.UltraWormholeMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/**
 * Keeps the Ultra Wormhole theme playing through a dimension change and stops every other sound instead.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftLevelChangeSoundMixin {
    @WrapOperation(method = "updateScreenAndTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private void hyperspace_wormholes$keepWarpMusic(SoundManager soundManager, Operation<Void> original) {
        SoundInstance music = UltraWormholeMusic.current();
        if (music == null || !soundManager.isActive(music)) {
            original.call(soundManager);
            return;
        }
        var channels = ((SoundEngineAccessor) ((SoundManagerAccessor) soundManager)
                .hyperspace_wormholes$getSoundEngine()).hyperspace_wormholes$getInstanceToChannel();
        for (SoundInstance sound : new ArrayList<>(channels.keySet())) {
            if (sound != music) soundManager.stop(sound);
        }
    }
}

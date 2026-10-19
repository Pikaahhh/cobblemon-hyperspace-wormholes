package dev.hyperspacewormholes.wormholes.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to the sound manager's sound engine.
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine")
    SoundEngine hyperspace_wormholes$getSoundEngine();
}

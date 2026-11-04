package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Access to the sound engine's playing channels, listener and whether it's loaded. */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> hyperspace_wormholes$getInstanceToChannel();

    @Accessor("listener")
    Listener hyperspace_wormholes$getListener();

    @Accessor("loaded")
    boolean hyperspace_wormholes$isLoaded();
}

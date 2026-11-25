package dev.hyperspacewormholes.wormholes.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/** Access to the sound engine's playing channels, how loud it plays a sound and whether it's loaded. */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> hyperspace_wormholes$getInstanceToChannel();

    @Invoker("calculateVolume")
    float hyperspace_wormholes$calculateVolume(SoundInstance sound);

    @Accessor("loaded")
    boolean hyperspace_wormholes$isLoaded();
}

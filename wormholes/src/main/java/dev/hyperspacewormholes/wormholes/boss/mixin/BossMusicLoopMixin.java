package dev.hyperspacewormholes.wormholes.boss.mixin;

import dev.hyperspacewormholes.wormholes.client.SeamlessLoopStream;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Streams the boss themes that have loop points (SeamlessLoopStream: Ultra Necrozma's, and Dusk Mane and Dawn Wings
 * Necrozma's) through that stream whenever they're played looping, so they play their opening once and then repeat
 * the loop body with no gap instead of restarting from the top. Done where every looping stream is opened, so
 * Cobblemon's battle music instance plays it unchanged, fades and all.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class BossMusicLoopMixin {
    @Shadow
    @Final
    private ResourceProvider resourceManager;

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$loopAtLoopPoints(ResourceLocation location, boolean looping,
                                                        CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!looping) return;
        SeamlessLoopStream.Loop loop = SeamlessLoopStream.loopFor(location);
        if (loop == null) return;
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                return new SeamlessLoopStream(resourceManager.open(location), loop);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool()));
    }
}

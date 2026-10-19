package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.sound.BattleMusicController;
import dev.hyperspacewormholes.hyperspace.Hyperspace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Cobblemon's battle music in Hyperspace so our music keeps playing.
 */
@Mixin(value = BattleMusicController.class, remap = false)
public abstract class BattleMusicControllerMixin {
    @Inject(method = {"initializeMusic", "switchMusic"}, at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepHyperspaceMusic(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.dimension().equals(Hyperspace.HYPERSPACE)) ci.cancel();
    }
}

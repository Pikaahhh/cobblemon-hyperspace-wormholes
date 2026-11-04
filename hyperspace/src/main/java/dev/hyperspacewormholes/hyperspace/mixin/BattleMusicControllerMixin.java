package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import dev.hyperspacewormholes.hyperspace.Hyperspace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Cobblemon's battle music in Hyperspace so our music keeps playing, all but Hoopa Unbound's theme, which is what
 * the event's silence is waiting for.
 */
@Mixin(value = BattleMusicController.class, remap = false)
public abstract class BattleMusicControllerMixin {
    @Inject(method = {"initializeMusic", "switchMusic"}, at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepHyperspaceMusic(BattleMusicInstance music, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Hyperspace.HYPERSPACE)) return;
        if (!music.getLocation().getPath().equals("music.hoopa_unbound_battle")) ci.cancel();
    }
}

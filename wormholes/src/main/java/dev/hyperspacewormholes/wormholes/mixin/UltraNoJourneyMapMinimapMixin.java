package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides JourneyMap's minimap on the Ultra Warp Ride and in the Ultra worlds, as UltraNoXaeroMinimapMixin does Xaero's. Its
 * settings and full map are left alone.
 */
@Pseudo
@Mixin(targets = "journeymap.client.ui.minimap.MiniMap", remap = false)
public class UltraNoJourneyMapMinimapMixin {
    @Inject(method = "drawMap*", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$notInUltraSpace(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && UltraZones.isUltraSpace(mc.level)) ci.cancel();
    }
}

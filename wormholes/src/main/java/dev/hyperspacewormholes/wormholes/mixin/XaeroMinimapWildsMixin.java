package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Xaero's Minimap in the Ultra Space Wilds, as the game shows no map there and the minimap's radar would give away
 * the Pokemon waiting as a shadow (WildsShadows). Hooks both of its renderer's render methods (the typed one and its
 * bridge).
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.module.MinimapRenderer", remap = false)
public class XaeroMinimapWildsMixin {
    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$notInWilds(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && UltraZones.isZone(mc.level)) ci.cancel();
    }
}

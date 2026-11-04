package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No Rustling Spots in the Ultra Space Wilds: the only Pokemon there is the one past the wormhole (WildsShadows).
 */
@Pseudo
@Mixin(targets = "net.levelscraft7.rustlingspots.spot.SpotSpawner", remap = false)
public class RustlingSpotsWildsMixin {
    @Inject(method = "trySpawnOneNearPlayer", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$notInWilds(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        if (UltraZones.isZone(level)) ci.cancel();
    }
}

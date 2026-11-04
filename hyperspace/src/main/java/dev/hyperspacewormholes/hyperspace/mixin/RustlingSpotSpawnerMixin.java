package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.Hyperspace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.levelscraft7.rustlingspots.spot.SpotSpawner", remap = false)
public class RustlingSpotSpawnerMixin {
    @Inject(method = "trySpawnOneNearPlayer", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$notInHyperspace(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        if (level.dimension() == Hyperspace.HYPERSPACE) ci.cancel();
    }
}

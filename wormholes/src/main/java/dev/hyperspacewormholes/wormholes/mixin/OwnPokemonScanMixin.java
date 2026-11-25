package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.net.serverhandling.pokedex.scanner.FinishScanningHandler;
import com.cobblemon.mod.common.net.serverhandling.pokedex.scanner.StartScanningHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A client can't name another player's own Pokemon to scan it (OwnEncounters).
 */
@Mixin(value = {StartScanningHandler.class, FinishScanningHandler.class}, remap = false)
public abstract class OwnPokemonScanMixin {
    @ModifyExpressionValue(method = "handle*", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;"))
    private Entity hyperspace_wormholes$notTheirs(Entity target, @Local(argsOnly = true) ServerPlayer player) {
        return target != null && OwnEncounters.hiddenFrom(target, player) ? null : target;
    }
}

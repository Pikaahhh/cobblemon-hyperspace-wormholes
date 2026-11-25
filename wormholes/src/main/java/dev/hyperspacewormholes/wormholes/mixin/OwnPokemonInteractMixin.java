package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.net.serverhandling.pokemon.interact.InteractPokemonHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A client can't name another player's own Pokemon to ride it or give it something (OwnEncounters).
 */
@Mixin(value = InteractPokemonHandler.class, remap = false)
public abstract class OwnPokemonInteractMixin {
    @ModifyExpressionValue(method = "handle*", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/server/level/ServerLevel;getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;"))
    private Entity hyperspace_wormholes$notTheirs(Entity target, @Local(argsOnly = true) ServerPlayer player) {
        return target != null && OwnEncounters.hiddenFrom(target, player) ? null : target;
    }
}

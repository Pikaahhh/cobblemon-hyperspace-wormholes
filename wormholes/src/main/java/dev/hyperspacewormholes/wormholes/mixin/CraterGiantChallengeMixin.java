package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.serverhandling.ChallengeHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.wormholes.ride.CraterGiants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Accepts a challenge to the Ultra Crater's giant catchable Celesteela from the end of the walk: Cobblemon's server only starts a wild battle when the player's look reaches the Pokemon within battleWildMaxDistance (12) with no block first, and the giant's body is further off than that from there, so a player looking at its body within CraterGiants.inReach counts too (the client side: mixin/CraterGiantReachMixin). */
@Mixin(value = ChallengeHandler.class, remap = false)
public abstract class CraterGiantChallengeMixin {
    @WrapOperation(method = "handle",
            at = @At(value = "INVOKE", remap = true, target = "Lcom/cobblemon/mod/common/util/PlayerExtensionsKt;canInteractWith(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/LivingEntity;F)Z"))
    private boolean hyperspace_wormholes$craterReach(ServerPlayer player, LivingEntity target, float maxDistance,
                                                     Operation<Boolean> original) {
        if (original.call(player, target, maxDistance)) return true;
        return target instanceof PokemonEntity giant && CraterGiants.inReach(player, giant);
    }
}

package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.BattleChallengePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.ride.CraterGiants;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a player send a Pokemon out at the Ultra Crater's giant catchable Celesteela from the end of the walk to start the
 * battle. Cobblemon's own send-out trace stops at the first block and wants a wild Pokemon's middle within
 * battleWildMaxDistance (12), so a player looking at the giant's body within CraterGiants.inReach challenges it instead.
 */
@Mixin(value = PartySendBinding.class, remap = false)
public abstract class CraterGiantReachMixin {
    @Inject(method = "checkForTargetInteractions", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$craterReach(LocalPlayer player, Pokemon pokemon, CallbackInfo ci) {
        if (player.getVehicle() != null) return;
        PokemonEntity giant = CraterGiants.lookedAt(player);
        if (giant == null || !giant.canBattle(player)) return;
        new BattleChallengePacket(giant.getId(), pokemon.getUuid(), BattleFormat.Companion.getGEN_9_SINGLES()).sendToServer();
        ci.cancel();
    }
}

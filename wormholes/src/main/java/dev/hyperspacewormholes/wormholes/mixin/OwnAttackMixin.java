package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The sounds, particles and drops of a player's blows at their own Pokemon are only theirs (OwnEncounters.only).
 */
@Mixin(Player.class)
public abstract class OwnAttackMixin {
    @WrapMethod(method = "attack")
    private void hyperspace_wormholes$onlyTheirs(Entity target, Operation<Void> original) {
        OwnEncounters.only(target, () -> original.call(target));
    }
}

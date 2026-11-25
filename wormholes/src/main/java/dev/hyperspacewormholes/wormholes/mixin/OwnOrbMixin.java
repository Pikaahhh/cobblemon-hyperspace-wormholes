package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

/**
 * Experience from a player's own Pokemon only goes to them and only joins their own (OwnEncounters; an orb's scope is its
 * owner's while it ticks).
 */
@Mixin(ExperienceOrb.class)
public abstract class OwnOrbMixin {
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void hyperspace_wormholes$sameOwner(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!Objects.equals(OwnEncounters.owner(orb), OwnEncounters.scope())) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "scanForEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"))
    private Player hyperspace_wormholes$toOwner(Player nearest) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        UUID owner = OwnEncounters.owner(orb);
        if (owner == null) return nearest;
        Player player = orb.level().getPlayerByUUID(owner);
        return player == null || player.isSpectator() || player.distanceToSqr(orb) > 64 ? null : player;
    }
}

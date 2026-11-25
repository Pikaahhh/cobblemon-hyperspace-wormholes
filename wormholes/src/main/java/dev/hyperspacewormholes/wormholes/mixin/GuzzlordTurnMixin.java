package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.RuinGuzzlord;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the Ultra Ruin's Guzzlord whole while it wakes (RuinGuzzlord.turns): a mob standing still turns its head, and each
 * client works out its body after that on its own, so without this the body would still be coming round as it roars.
 */
@Mixin(BodyRotationControl.class)
public abstract class GuzzlordTurnMixin {
    @Shadow
    @Final
    private Mob mob;

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$turnWhole(CallbackInfo ci) {
        if (mob instanceof PokemonEntity pokemon && RuinGuzzlord.turns(pokemon)) {
            mob.yBodyRot = mob.getYRot();
            ci.cancel();
        }
    }
}

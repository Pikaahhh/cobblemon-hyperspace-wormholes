package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.Targetable;
import dev.hyperspacewormholes.wormholes.boss.BossActor;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a trainer's Pokemon out on the spot reserved for that trainer in a boss fight (Boss.reserve) instead of
 * Cobblemon's spot between the trainer and the boss. In a lopsided fight the Pokemon opposite is simply the first on the
 * other side: Cobblemon's own lookup wants a slot number only an even doubles has, and throws inside the send-out's
 * future, which leaves the Pokemon in its ball and the battle waiting for it forever.
 */
@Mixin(value = ActiveBattlePokemon.class, remap = false)
public abstract class BossSendOutMixin {
    @Inject(method = "getSendOutPosition", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$reservedSpot(CallbackInfoReturnable<Vec3> cir) {
        Vec3 at = Bosses.sendOutAt((ActiveBattlePokemon) (Object) this);
        if (at != null) cir.setReturnValue(at);
    }

    @Inject(method = "getOppositeOpponent", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$opposite(CallbackInfoReturnable<Targetable> cir) {
        Targetable opposite = BossActor.opposite((ActiveBattlePokemon) (Object) this);
        if (opposite != null) cir.setReturnValue(opposite);
    }
}

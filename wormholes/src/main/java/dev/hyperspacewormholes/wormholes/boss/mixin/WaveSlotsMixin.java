package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import dev.hyperspacewormholes.wormholes.boss.BossActor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a lopsided boss battle its one slot a side between Cobblemon making them and Showdown starting. It has to be
 * here: Graal runs the battle's opening inside the startBattle call, on this thread, so the first switch-ins have been
 * interpreted by the time BattleRegistry.startBattle returns to Boss.join.
 */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class WaveSlotsMixin {
    @Inject(method = "startShowdown", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/battles/runner/ShowdownService;startBattle(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;[Ljava/lang/String;)V"))
    private void hyperspace_wormholes$waveSlots(PokemonBattle battle, CallbackInfo ci) {
        BossActor.fitSlots(battle);
    }
}

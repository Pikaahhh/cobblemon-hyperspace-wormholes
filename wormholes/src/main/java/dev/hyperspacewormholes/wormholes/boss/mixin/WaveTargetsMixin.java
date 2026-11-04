package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.client.BossClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cobblemon's target picker lays out both rows by the size of the chooser's side, which puts every legendary on one tile when a trainer alone faces a wave (LegendWave). It uses the bigger side instead; on even sides that is the same number. In a fight like that it also leaves off the slots of legendaries that have fainted. */
@Mixin(value = BattleTargetSelection.class, remap = false)
public abstract class WaveTargetsMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lkotlin/collections/CollectionsKt;count(Ljava/lang/Iterable;)I"))
    private int hyperspace_wormholes$biggerSide(int ownSide, @Local(argsOnly = true) SingleActionRequest request) {
        return Math.max(ownSide, request.getActivePokemon().getAllActivePokemon().size() - ownSide);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hyperspace_wormholes$dropFainted(CallbackInfo ci) {
        BattleTargetSelection self = (BattleTargetSelection) (Object) this;
        if (!BossClient.lopsided(self.getRequest().getActivePokemon())) return;
        self.setTargetTiles(self.getTargetTiles().stream().filter(tile -> BossClient.standing(tile.getTarget())).toList());
    }
}

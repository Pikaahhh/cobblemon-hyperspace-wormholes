package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import dev.hyperspacewormholes.wormholes.client.BossClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boss fights are doubles underneath (BossActor.fitSlots), so Cobblemon opens its target picker for every move. With
 * only one foe standing the move goes straight in instead.
 */
@Mixin(value = BattleGUI.class, remap = false)
public abstract class OneTargetMixin {
    @Inject(method = "changeActionSelection", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$skipPicker(BattleActionSelection selection, CallbackInfo ci) {
        if (!(selection instanceof BattleTargetSelection picker) || !BossClient.nothingToPick(picker)) return;
        ci.cancel();
        ((BattleGUI) (Object) this).selectAction(picker.getRequest(),
                new MoveActionResponse(picker.getMove().getId(), BossClient.onlyTarget(picker), picker.getGimmickID()));
    }
}

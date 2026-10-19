package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fixes Cobblemon's battle overlay crashing if it gets drawn right after a battle ends.
 */
@Mixin(value = BattleOverlay.class, remap = false)
public class BattleOverlayMixin {
    @WrapMethod(method = "method_1753")
    private void hyperspace_wormholes$battleGone(GuiGraphics context, DeltaTracker delta, Operation<Void> original) {
        try {
            original.call(context, delta);
        } catch (NullPointerException e) {
            if (CobblemonClient.INSTANCE.getBattle() != null) throw e;
        }
    }
}

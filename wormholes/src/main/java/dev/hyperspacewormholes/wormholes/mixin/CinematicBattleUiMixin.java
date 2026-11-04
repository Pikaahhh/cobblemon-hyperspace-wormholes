package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon's battle screen (move and target choice, the battle log) draws nothing while one of Necrozma's cutscenes
 * shows; its overlay is part of the HUD, which CinematicHudMixin already replaces.
 */
@Mixin(BattleGUI.class)
public abstract class CinematicBattleUiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideForCinematic(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Cinematics.isShowing()) ci.cancel();
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideBackgroundForCinematic(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Cinematics.isShowing()) ci.cancel();
    }
}

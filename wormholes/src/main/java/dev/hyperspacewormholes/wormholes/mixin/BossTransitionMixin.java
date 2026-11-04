package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.client.BossTransition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the boss screen transition (BossTransition) over everything and hides Cobblemon's battle screen meanwhile. */
@Mixin(GameRenderer.class)
public abstract class BossTransitionMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void hyperspace_wormholes$hideBattleScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                                       float delta, Operation<Void> original) {
        if (screen instanceof BattleGUI && BossTransition.hidesUi()) return;
        original.call(screen, graphics, mouseX, mouseY, delta);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void hyperspace_wormholes$transitionOverEverything(DeltaTracker delta, boolean renderLevel, CallbackInfo ci,
                                                               @Local GuiGraphics graphics) {
        BossTransition.draw(graphics, delta);
    }
}

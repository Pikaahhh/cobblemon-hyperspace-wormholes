package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.BossHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the boss HP bar (BossHud) where vanilla's boss bars go, in their stead while it shows, stretched across the
 * centre of the screen to match the width of the normal boss bar.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$bossHud(GuiGraphics graphics, CallbackInfo ci) {
        if (BossHud.draw(graphics, Minecraft.getInstance().getTimer())) ci.cancel();
    }
}

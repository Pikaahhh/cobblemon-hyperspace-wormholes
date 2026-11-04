package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.render.RenderHelperKt;
import dev.hyperspacewormholes.wormholes.client.BossClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws what a trainer's locked menu in a boss fight is waiting for (BossClient.waitHint: the others' picks) one line
 * under Cobblemon's "Press ` to move around." hint on the battle screen, in the same place, size and opacity as that
 * hint.
 */
@Mixin(BattleGUI.class)
public abstract class BossWaitHintMixin {
    // render is Screen's method, so this mixin goes through the refmap
    @Inject(method = "render", at = @At("TAIL"))
    private void hyperspace_wormholes$waitHint(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MutableComponent hint = BossClient.waitHint();
        if (hint == null) return;
        BattleGUI gui = (BattleGUI) (Object) this;
        if (gui.getCurrentActionSelection() != null) return;
        float opacity = 0.75F * gui.getOpacity();
        if (opacity < 0.05F) return;
        Minecraft mc = Minecraft.getInstance();
        RenderHelperKt.drawScaledText(context, null, hint, mc.getWindow().getGuiScaledWidth() / 2,
                mc.getWindow().getGuiScaledHeight() / 5 + mc.font.lineHeight + 2, 1F, opacity, Integer.MAX_VALUE,
                0x00FFFFFF + ((int) (opacity * 255) << 24), true, false, null, null);
    }
}

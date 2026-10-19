package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.CobblemonResources;
import dev.hyperspacewormholes.hyperspace.MoveNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Shrinks and wraps long move names on Cobblemon's battle move tiles so they fit and don't run into the PP.
 */
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection$MoveTile")
public class MoveTileMixin {
    private static final int NAME_X = 17;
    private static final int NAME_SHIFT = 3;
    private static final int COLOURED_BOTTOM_END = 44;
    private static final int TILE_WIDTH = 92;
    private static final int PP_CENTRE = 75;
    private static final int GAP = 3;

    @Shadow(remap = false)
    @Final
    private InBattleMove move;

    @Redirect(method = "render", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V"))
    private void hyperspace_wormholes$fitName(GuiGraphics graphics, ResourceLocation font, MutableComponent text, Number x, Number y, float scale,
                                  Number opacity, int maxWidth, int colour, boolean centred, boolean shadow, Integer mouseX, Integer mouseY,
                                  int defaults, Object marker) {
        Font renderer = Minecraft.getInstance().font;
        MutableComponent name = text.withStyle(style -> style.withFont(font));
        MutableComponent pp = Component.literal(move.getPp() + "/" + move.getMaxpp())
                .withStyle(style -> style.withBold(true).withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
        int start = NAME_X - NAME_SHIFT;
        int room = TILE_WIDTH - start - 4;
        int secondRoom = Math.min(PP_CENTRE - renderer.width(pp) / 2 - GAP, COLOURED_BOTTOM_END) - start;
        int alpha = Mth.clamp((int) (opacity.floatValue() * 255), 5, 255);
        MoveNames.draw(graphics, renderer, name, x.floatValue() - NAME_SHIFT, y.floatValue(), room, secondRoom, alpha << 24 | 0xFFFFFF);
    }
}

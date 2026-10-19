package dev.hyperspacewormholes.hyperspace;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Fits a move name into a tile by shrinking it and wrapping it onto a second line if it has to, keeping clear of the
 * text to its right.
 */
public class MoveNames {
    private static final float MIN_SCALE = 0.5f;
    private static final float SCALE_STEP = 0.05f;

    public static void draw(GuiGraphics graphics, Font font, Component name, float x, float y, int room, int secondRoom, int colour) {
        for (float scale = 1f; scale >= MIN_SCALE; scale -= SCALE_STEP) {
            List<FormattedCharSequence> lines = font.split(name, (int) (room / scale));
            boolean fits = lines.size() == 1 || lines.size() == 2 && font.width(lines.get(1)) * scale <= secondRoom;
            if (!fits && scale - SCALE_STEP >= MIN_SCALE) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1f);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), 0, i * (font.lineHeight - 1), colour, true);
            }
            graphics.pose().popPose();
            return;
        }
    }
}

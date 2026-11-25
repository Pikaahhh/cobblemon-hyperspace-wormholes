package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.Window;
import dev.hyperspacewormholes.wormholes.client.WarpEntryClose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The white circle of a wormhole closing behind the legend, as pixel art: the light's white point, the ball of its
 * flash (both drawn by WarpCloseLook) and, at the end, the disc the point swells into over the whole picture and the
 * HUD, until every corner is white and the plain white of the trip takes over (WarpEntryClose.white).
 * <p>
 * It's made of square cells round its middle, all one size in the picture: that of the spirals' pixels, in whole pixels
 * of the screen. The circle's size changes smoothly and its edge a cell at a time. Round it are two flat bands in the
 * light's own colours, RIM cells each.
 */
public final class WarpWhite {
    /** One rectangle of cells, from x0, y0 to x1, y1 (y upwards), of the white (0) or of a band round it (1 or 2). */
    public interface Cells {
        void fill(int x0, int y0, int x1, int y1, int part);
    }

    static final float SWELLS_FROM = 37.5f, COVERED = 50f;
    private static final int RIM = 2;
    private static final float RIM_FROM = 38.3f, RIM_EACH = 1.3f;

    private static int cell = 1, middleX, middleY, left, right, down, up;
    private static float point;
    private static float[] inner = {1, 1, 1}, outer = {1, 1, 1};

    private WarpWhite() {}

    /** Where the circle's middle is from the picture's, a cell's size and the point's radius, in half heights of it. */
    static void at(float x, float y, float pixel, float pointRadius, float[] ball, float[] ring) {
        Window window = Minecraft.getInstance().getWindow();
        int wide = window.getWidth(), high = window.getHeight();
        cell = Math.max(1, Math.round(pixel * high / 2f));
        middleX = Math.round(wide / 2f + x * high / 2f);
        middleY = Math.round(high / 2f - y * high / 2f);
        left = Mth.ceil(middleX / (float) cell) + 1;
        right = Mth.ceil((wide - middleX) / (float) cell) + 1;
        up = Mth.ceil(middleY / (float) cell) + 1;
        down = Mth.ceil((high - middleY) / (float) cell) + 1;
        point = cells(pointRadius);
        inner = ball;
        outer = ring;
    }

    /** A length in half heights of the picture, in cells. */
    static float cells(float halfHeights) {
        return halfHeights * Minecraft.getInstance().getWindow().getHeight() / 2f / cell;
    }

    /** One cell in half heights of the picture. */
    static float cell() {
        return cell * 2f / Minecraft.getInstance().getWindow().getHeight();
    }

    /** The middle, on its whole pixel, from the picture's middle in half heights of it. */
    static float snappedX() {
        Window window = Minecraft.getInstance().getWindow();
        return (middleX * 2f - window.getWidth()) / window.getHeight();
    }

    static float snappedY() {
        int high = Minecraft.getInstance().getWindow().getHeight();
        return (high - middleY * 2f) / high;
    }

    /** How many cells from the middle the picture's furthest corner is, and one more. */
    static float toCorner() {
        float x = Math.max(left, right), y = Math.max(up, down);
        return Mth.sqrt(x * x + y * y) + 1;
    }

    /** Whether the disc at the end shows. */
    public static boolean swelling(float partialTick) {
        return point > 0 && WarpEntryClose.whitening() && WarpEntryClose.frame(partialTick) >= SWELLS_FROM;
    }

    /** The disc at the end, in cells round the middle. */
    public static void draw(Cells out, float partialTick) {
        float frame = WarpEntryClose.frame(partialTick);
        float t = Mth.clamp((frame - SWELLS_FROM) / (COVERED - SWELLS_FROM), 0, 1);
        float radius = point * (float) Math.pow(Math.max(1, toCorner() / point), Math.pow(t, 1.6));
        int rim = Mth.floor((frame - RIM_FROM) / RIM_EACH) + 1;
        circle(out, radius, Mth.clamp(rim, 0, RIM), Mth.clamp(rim - RIM, 0, RIM));
    }

    /** A circle and the bands round it that many cells wide, cut off at the edges of the picture. */
    static void circle(Cells out, float radius, int first, int second) {
        float middle = radius + first, edge = middle + second;
        int top = Math.min(up, Mth.ceil(edge)), row = -Math.min(down, Mth.ceil(edge));
        while (row < top) {
            int a = reach(radius, row), b = Math.max(a, reach(middle, row)), c = Math.max(b, reach(edge, row)), to = row + 1;
            while (to < top && reach(radius, to) == a && Math.max(a, reach(middle, to)) == b && Math.max(b, reach(edge, to)) == c) to++;
            span(out, -a, a, row, to, 0);
            span(out, -b, -a, row, to, 1);
            span(out, a, b, row, to, 1);
            span(out, -c, -b, row, to, 2);
            span(out, b, c, row, to, 2);
            row = to;
        }
    }

    private static void span(Cells out, int from, int to, int y0, int y1, int part) {
        int x0 = Math.max(-left, from), x1 = Math.min(right, to);
        if (x0 < x1) out.fill(x0, y0, x1, y1, part);
    }

    /**
     * How many cells across from its middle a circle of this radius reaches in this row: those whose own middle is in it,
     * but for a lone pair at its top, bottom or side.
     */
    private static int reach(float radius, int row) {
        boolean big = radius > 3;
        float high = Math.max(Math.abs(row + 0.5f), big ? 1.5f : 0), wide = radius * radius - high * high;
        int cells = wide <= 0 ? 0 : Mth.floor(Mth.sqrt(wide) + 0.5f);
        return big && cells < 2 ? 0 : cells;
    }

    /** On the screen, in its pixels: how big a cell is and where the middle is. */
    public static int cellPixels() {
        return cell;
    }

    public static int middleX() {
        return middleX;
    }

    public static int middleY() {
        return middleY;
    }

    public static int colour(int part) {
        if (part == 0) return 0xFFFFFFFF;
        float[] c = part == 1 ? inner : outer;
        return 0xFF000000 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
    }
}

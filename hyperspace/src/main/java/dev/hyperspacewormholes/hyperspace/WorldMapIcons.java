package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * Draws the map icons (MapIcons) on Xaero's World Map: in the Overworld each spatial distortion as its icon (its
 * outline is in the texture), at 1.125x an item's size, a little bigger under the mouse. Hovering one shows its stars
 * and types; a click is left to the map (mixin/XaeroWorldMapMixin passes the map's camera, zoom, screen scale and shown
 * dimension). The map's own point to screen maths is copied: a block x sits (x - camera x) x scale framebuffer pixels
 * from the middle of the window, divided by the map's screen scale (the GUI scale) for screen coordinates.
 */
public final class WorldMapIcons {
    private static final float ICON = 1.125f;
    private static final float HOVERED = 1.25f;
    private static final double REACH = 10;

    private WorldMapIcons() {}

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, double cameraX, double cameraZ, double scale,
                              double screenScale, ResourceKey<Level> shown) {
        if (shown == null || MapIcons.marks(shown).isEmpty()) return;
        Function<BlockPos, double[]> screen = screen(cameraX, cameraZ, scale, screenScale);
        MapDistortions.Mark hovered = MapIcons.under(mouseX, mouseY, REACH, shown, screen);
        for (MapDistortions.Mark mark : MapIcons.marks(shown)) {
            draw(graphics, screen.apply(mark.pos()), mark == hovered);
        }
        if (hovered != null) graphics.renderComponentTooltip(Minecraft.getInstance().font, MapIcons.tooltip(hovered), mouseX, mouseY);
    }

    private static void draw(GuiGraphics graphics, double[] at, boolean hovered) {
        float size = hovered ? ICON * HOVERED : ICON;
        graphics.pose().pushPose();
        graphics.pose().translate((float) at[0], (float) at[1], 0);
        graphics.pose().scale(size, size, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(MapIcons.DISTORTION, -8, -8, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static Function<BlockPos, double[]> screen(double cameraX, double cameraZ, double scale, double screenScale) {
        var window = Minecraft.getInstance().getWindow();
        double guiScale = screenScale > 0 ? screenScale : window.getGuiScale();
        return pos -> new double[]{(window.getWidth() / 2 + (pos.getX() + 0.5 - cameraX) * scale) / guiScale,
                (window.getHeight() / 2 + (pos.getZ() + 0.5 - cameraZ) * scale) / guiScale};
    }
}

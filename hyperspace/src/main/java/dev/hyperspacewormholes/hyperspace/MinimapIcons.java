package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Draws the map icons (MapIcons) on Xaero's Minimap with no waypoints made. mixin/XaeroMinimapMixin calls this right
 * after the minimap draws its own icons (its "over map" element pass); each distortion is placed with the minimap's
 * translatePosition and that pass's rotation, zoom, size and shape, so it turns and zooms like the radar icons. Icons off
 * the edge aren't drawn, and only while the player and the minimap are in the Overworld. Xaero's Minimap is reached by
 * reflection, so it isn't needed to build or run.
 */
public final class MinimapIcons {
    private static final String OVER_MAP = "xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler";
    private static final float ICON = 0.6f;
    private static boolean failed;
    private static Class<?> overMap;
    private static Field ps, pc, zoom, halfViewW, halfViewH, specW, specH, circle, optionalScale, partialTranslate;
    private static Method translate;

    private MinimapIcons() {}

    public static void render(Object handler, GuiGraphics graphics, Vec3 renderPos, ResourceKey<Level> shown) {
        if (failed || MapIcons.marks(shown).isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || shown == null || !minecraft.level.dimension().equals(shown)) return;
        try {
            if (overMap == null) lookUp();
            if (!overMap.isInstance(handler)) return;
            float scale = optionalScale.getFloat(handler) * ICON;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (MapDistortions.Mark mark : MapIcons.marks(shown)) draw(handler, graphics, mark.pos(), renderPos, scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            Hyperspace.LOGGER.warn("Couldn't draw map icons on Xaero's Minimap; leaving them off", e);
        }
    }

    private static void draw(Object handler, GuiGraphics graphics, BlockPos pos, Vec3 renderPos, float scale)
            throws ReflectiveOperationException {
        PoseStack stack = graphics.pose();
        double[] partial = (double[]) partialTranslate.get(handler);
        stack.pushPose();
        try {
            boolean outside = (Boolean) translate.invoke(null, stack, specW.getInt(handler), specH.getInt(handler),
                    halfViewW.getInt(handler), halfViewH.getInt(handler), ps.getDouble(handler), pc.getDouble(handler),
                    pos.getX() + 0.5 - renderPos.x, pos.getZ() + 0.5 - renderPos.z, zoom.getDouble(handler),
                    circle.getBoolean(handler), partial);
            if (outside) return;
            stack.translate(partial[0], partial[1], 0);
            stack.scale(scale, scale, 1);
            graphics.blit(MapIcons.DISTORTION, -8, -8, 0, 0, 16, 16, 16, 16);
        } finally {
            stack.popPose();
        }
    }

    private static void lookUp() throws ReflectiveOperationException {
        overMap = Class.forName(OVER_MAP);
        ps = field("ps");
        pc = field("pc");
        zoom = field("zoom");
        halfViewW = field("halfViewW");
        halfViewH = field("halfViewH");
        specW = field("specW");
        specH = field("specH");
        circle = field("circle");
        optionalScale = field("optionalScale");
        partialTranslate = field("partialTranslate");
        translate = overMap.getMethod("translatePosition", PoseStack.class, int.class, int.class, int.class, int.class,
                double.class, double.class, double.class, double.class, double.class, boolean.class, double[].class);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = overMap.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

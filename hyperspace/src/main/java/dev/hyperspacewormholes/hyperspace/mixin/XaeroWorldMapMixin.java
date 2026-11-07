package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.WorldMapIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the distortion icons on Xaero's World Map (WorldMapIcons). Hooks the map screen's render (Screen.render, named by
 * hand both ways since another mod's class isn't remapped: method_25394 in a real game, render under Mojang names) and
 * reads its camera position, zoom, screen scale and shown dimension. Optional (hyperspace_wormholes.maps.mixins.json):
 * without Xaero's World Map the target isn't there and nothing applies.
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroWorldMapMixin {
    @Shadow private double cameraX;
    @Shadow private double cameraZ;
    @Shadow private double scale;
    @Shadow private double screenScale;
    @Shadow private ResourceKey<Level> lastViewedDimensionId;

    @Inject(method = {"method_25394", "render"}, at = @At("TAIL"), require = 0)
    private void hyperspace_wormholes$icons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        WorldMapIcons.render(graphics, mouseX, mouseY, cameraX, cameraZ, scale, screenScale, lastViewedDimensionId);
    }
}

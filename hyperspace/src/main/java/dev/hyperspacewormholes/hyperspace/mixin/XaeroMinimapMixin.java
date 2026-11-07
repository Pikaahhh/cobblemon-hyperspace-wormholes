package dev.hyperspacewormholes.hyperspace.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hyperspacewormholes.hyperspace.MinimapIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets MinimapIcons draw the distortion icons on Xaero's Minimap at the end of each of its element passes (it only
 * draws in the pass over the map), in the same place as the minimap's own icons. Another mod's class isn't remapped and
 * this config has no refmap, so the selector is written out both ways: intermediary names in a real game, Mojang names
 * in a dev run. Optional (hyperspace_wormholes.maps.mixins.json): without Xaero's Minimap nothing applies.
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.element.render.MinimapElementRendererHandler", remap = false)
public class XaeroMinimapMixin {
    @Inject(method = {"render(Lnet/minecraft/class_332;Lnet/minecraft/class_243;FLnet/minecraft/class_276;DLnet/minecraft/class_5321;)V",
            "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/phys/Vec3;FLcom/mojang/blaze3d/pipeline/RenderTarget;DLnet/minecraft/resources/ResourceKey;)V"},
            at = @At("RETURN"), require = 0)
    private void hyperspace_wormholes$icons(GuiGraphics graphics, Vec3 renderPos, float partialTick, RenderTarget framebuffer,
                                            double backgroundCoordinateScale, ResourceKey<Level> mapDimension, CallbackInfo ci) {
        MinimapIcons.render(this, graphics, renderPos, mapDimension);
    }
}

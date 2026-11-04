package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leaves out the entities Necrozma's cutscenes hide (Cinematics.hides): other Pokemon, players in close-ups, Necrozma. */
@Mixin(EntityRenderDispatcher.class)
public abstract class CinematicHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideForCinematic(Entity entity, Frustum frustum, double x, double y, double z,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (Cinematics.hides(entity)) cir.setReturnValue(false);
    }
}

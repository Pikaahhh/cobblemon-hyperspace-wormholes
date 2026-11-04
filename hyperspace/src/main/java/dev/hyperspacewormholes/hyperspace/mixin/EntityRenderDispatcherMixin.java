package dev.hyperspacewormholes.hyperspace.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.hyperspace.HyperspaceGrey;
import dev.hyperspacewormholes.hyperspace.OtherPlayersLegendaries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips drawing stuff that the greyscale pass draws later in colour, and other people's hidden bosses. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$colourLater(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                      PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (HyperspaceGrey.hidden(entity) || OtherPlayersLegendaries.hides(entity)) ci.cancel();
    }
}

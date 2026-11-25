package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.hyperspace.HoopaClip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Tells HoopaClip which Pokemon Cobblemon is drawing (and from where the camera looks) and hands the rest of the draw its
 * clipping buffers, so a Pokemon coming out of a Hoopa ring has its part behind the ring cut away. The typed render is
 * the class's first render (the others are its bridges), so the bare name finds it.
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class PokemonRendererClipMixin {
    @WrapMethod(method = "render")
    private void hyperspace_wormholes$clip(PokemonEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                           int light, Operation<Void> original) {
        HoopaClip.begin(entity, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        try {
            original.call(entity, yaw, partialTick, pose, HoopaClip.wrap(buffers), light);
        } finally {
            HoopaClip.end();
        }
    }
}

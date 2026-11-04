package dev.hyperspacewormholes.usum.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.usum.client.ModelMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shifts and turns a Pokemon's drawn model while a move's model motion plays (client.ModelMotion): the pose is pushed and
 * moved as Cobblemon starts drawing the Pokemon and popped when it is done. render keeps its own name in the
 * intermediary jar (only its bridges are remapped) and it's the class's first render, so the bare name finds it with no
 * Minecraft types in the selector. Optional (require 0).
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class ModelMotionMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void horizons_movefx$motionStart(PokemonEntity entity, float yaw, float partialTicks, PoseStack pose,
                                             MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelMotion.begin(entity, partialTicks, pose);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void horizons_movefx$motionEnd(PokemonEntity entity, float yaw, float partialTicks, PoseStack pose,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelMotion.end(pose);
    }
}

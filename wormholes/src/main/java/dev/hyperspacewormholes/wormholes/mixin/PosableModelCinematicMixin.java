package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands each posed Pokemon model to Necrozma's cutscenes and aura every frame it is drawn (Cinematics.trackModel), so the
 * Ultra Burst's light ball stays centred on the fused Necrozma, the defeat's golden ball on the lying Ultra Necrozma,
 * and the aura on Ultra Necrozma's outline.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelCinematicMixin {
    @Inject(method = "applyAnimations", at = @At("TAIL"))
    private void hyperspace_wormholes$trackUltraBurstDome(Entity entity, PosableState state, float limbSwing,
                                                         float limbSwingAmount, float ageInTicks, float headYaw,
                                                         float headPitch, CallbackInfo ci) {
        if (entity != null) Cinematics.trackModel(entity, (PosableModel) (Object) this, state);
    }
}

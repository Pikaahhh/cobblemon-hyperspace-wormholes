package dev.hyperspacewormholes.usum.mixin;

import com.cobblemon.mod.common.client.net.effect.SpawnSnowstormEntityParticleHandler;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import dev.hyperspacewormholes.usum.client.HitCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before Cobblemon places an entity particle effect, gives the user and the target a body-centre "middle" and "target"
 * locator when their model has none (HitCheck.ensureBodyLocators), so nothing aimed at the body lands on the feet.
 * handle is the class's first method of that name (the other is its bridge), so the bare name finds it with no
 * Minecraft types in the selector. Optional (require 0).
 */
@Mixin(value = SpawnSnowstormEntityParticleHandler.class, remap = false)
public abstract class LocatorFallbackMixin {
    @Inject(method = "handle", at = @At("HEAD"), require = 0)
    private void horizons_movefx$bodyLocators(SpawnSnowstormEntityParticlePacket packet, Minecraft client, CallbackInfo ci) {
        ClientLevel level = client.level;
        if (level == null) return;
        HitCheck.ensureBodyLocators(level.getEntity(packet.getSourceEntityId()));
        Integer target = packet.getTargetedEntityId();
        if (target != null) HitCheck.ensureBodyLocators(level.getEntity(target));
    }
}

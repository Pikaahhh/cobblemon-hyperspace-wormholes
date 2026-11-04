package dev.hyperspacewormholes.usum.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import dev.hyperspacewormholes.usum.client.MoveFxClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the camera a little by the move animations' camera shake (MoveFxClient.shake), once it has been placed each
 * frame (after the battle camera), and only while the player is held in a battle's view: in any battle whose screen
 * is up, never while it is minimised to move around (Cobblemon's "Press ` to move around") or outside a battle.
 * Optional (require 0), so a camera mod that changes the method only loses the shake.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void horizons_movefx$shake(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                       float partialTick, CallbackInfo ci) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || battle.getMinimised()) return;
        float[] shake = MoveFxClient.shake(partialTick);
        if (shake == null) return;
        setRotation(yRot + shake[0], xRot + shake[1]);
    }
}

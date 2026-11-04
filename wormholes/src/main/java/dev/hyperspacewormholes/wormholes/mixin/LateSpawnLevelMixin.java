package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.LateSpawns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies whatever came for an entity before it was added on the client (LateSpawns) once it is. */
@Mixin(ClientLevel.class)
public abstract class LateSpawnLevelMixin {
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void hyperspace_wormholes$late(Entity entity, CallbackInfo ci) {
        LateSpawns.added((ClientLevel) (Object) this, entity);
    }
}

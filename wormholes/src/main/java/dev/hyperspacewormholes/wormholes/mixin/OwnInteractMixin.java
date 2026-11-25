package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A client can't name another player's own Pokemon to hit, use, pick or watch it (OwnEncounters).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class OwnInteractMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyExpressionValue(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;getTarget(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"))
    private Entity hyperspace_wormholes$notTheirs(Entity target) {
        return target != null && OwnEncounters.hiddenFrom(target, player) ? null : target;
    }
}

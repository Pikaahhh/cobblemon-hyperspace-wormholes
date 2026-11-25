package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * A player's own Pokemon only notices that player (OwnEncounters).
 */
@Mixin(PlayerSensor.class)
public abstract class OwnPlayerSensorMixin {
    @ModifyExpressionValue(method = "doTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<ServerPlayer> hyperspace_wormholes$without(List<ServerPlayer> players, @Local(argsOnly = true) LivingEntity body) {
        return OwnEncounters.without(body, players);
    }
}

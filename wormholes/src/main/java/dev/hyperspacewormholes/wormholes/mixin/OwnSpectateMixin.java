package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.net.messages.server.battle.SpectateBattlePacket;
import com.cobblemon.mod.common.net.serverhandling.battle.SpectateBattleHandler;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A battle with a player's own Pokemon can't be watched by anyone else (OwnEncounters.mayWatch).
 */
@Mixin(value = SpectateBattleHandler.class, remap = false)
public abstract class OwnSpectateMixin {
    @Inject(method = "handle",
            at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$notTheirs(SpectateBattlePacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (hyperspace_wormholes$refused(BattleRegistry.getBattleByParticipatingPlayerId(packet.getTargetedEntityId()), player)) ci.cancel();
    }

    @Inject(method = "spectateBattle", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$notTheirs(ServerPlayer target, ServerPlayer player, CallbackInfo ci) {
        if (hyperspace_wormholes$refused(BattleRegistry.getBattleByParticipatingPlayer(target), player)) ci.cancel();
    }

    @Unique
    private static boolean hyperspace_wormholes$refused(PokemonBattle battle, ServerPlayer player) {
        if (OwnEncounters.mayWatch(battle, player)) return false;
        player.sendSystemMessage(Component.translatable("cobblemon.ui.interact.failed").withStyle(ChatFormatting.YELLOW));
        return true;
    }
}

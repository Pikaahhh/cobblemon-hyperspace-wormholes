package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.boss.Boss;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Turns a player's usual wild battle with a boss Pokemon (the battle key, or their Pokemon sent out at it) into joining
 * that boss's fight: their own battle against a copy of it that shares its HP (Boss.join).
 */
@Mixin(BattleBuilder.class)
public abstract class BossJoinMixin {
    // the bare name is the full overload, first in the class, which the others end up in; a descriptor would name
    // ServerPlayer, another class in production
    @Inject(method = "pve", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$joinBoss(ServerPlayer player, PokemonEntity entity, UUID lead, BattleFormat format, boolean clone,
            boolean heal, float flee, PartyStore party, CallbackInfoReturnable<BattleStartResult> cir) {
        Boss boss = Bosses.of(entity);
        if (boss != null) cir.setReturnValue(boss.join(player, lead));
    }
}

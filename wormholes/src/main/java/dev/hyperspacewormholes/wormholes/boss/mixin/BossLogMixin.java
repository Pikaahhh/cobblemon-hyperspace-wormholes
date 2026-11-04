package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows every line a trainer's battle against a boss writes in its battle log in the boss's other battles too
 * (Bosses.forward). Only the text travels, so nobody else's boosts or damage touch a trainer's own battle.
 */
@Mixin(value = PokemonBattle.class, remap = false)
public abstract class BossLogMixin {
    @Inject(method = "broadcastChatMessage", at = @At("TAIL"))
    private void hyperspace_wormholes$shareLine(Component component, CallbackInfo ci) {
        Bosses.forward((PokemonBattle) (Object) this, component);
    }
}

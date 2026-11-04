package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import dev.hyperspacewormholes.wormholes.client.BossHud;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out Cobblemon's info tile for the opposing side's Pokemon while the boss HP bar (BossHud) shows. Cancelling the
 * tile also skips what other mods draw at its end, such as Mega Showdown's stat change boxes, which the boss bar shows under
 * itself instead. It still runs the tile's animations, or a wave's first legendary would never replace the boss in its slot.
 */
@Mixin(value = BattleOverlay.class, remap = false)
public abstract class BossTileMixin {
    @Inject(method = "drawTile", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideBossTile(GuiGraphics context, float tickDelta, ActiveClientBattlePokemon pokemon,
                                                   boolean left, int rank, PokedexEntryProgress dexState, boolean hasCommand,
                                                   boolean isHovered, boolean isCompact, CallbackInfo ci) {
        if (!left && BossHud.hidesOpponentTiles()) {
            // the tile's animations are also what swaps a new Pokemon into its slot, which the target picker reads
            pokemon.animate(tickDelta);
            ci.cancel();
        }
    }
}

package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The name under a tile in the target picker is its actor's, which says Hoopa under every legendary of a wave
 * (LegendWave). A wild tile names its own Pokemon instead.
 */
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection$TargetTile", remap = false)
public abstract class WaveTileNameMixin {
    @Shadow
    @Final
    private ActiveClientBattlePokemon target;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/battle/ClientBattleActor;getDisplayName"))
    private MutableComponent hyperspace_wormholes$ownName(MutableComponent actorName) {
        ClientBattlePokemon pokemon = target.getBattlePokemon();
        return pokemon != null && target.getActor().getType() == ActorType.WILD ? pokemon.getDisplayName() : actorName;
    }
}

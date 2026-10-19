package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.ActivateInstruction;
import dev.hyperspacewormholes.hyperspace.Spawns;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Hyperspace Guard's "A powerful force prevents you from knocking out this Pokemon!" line to the battle log when
 * it saves a Pokemon.
 */
@Mixin(value = ActivateInstruction.class, remap = false)
public abstract class ActivateInstructionMixin {
    @Shadow @Final private BattleMessage message;

    @Inject(method = "postActionEffect", at = @At("TAIL"))
    private void hyperspace_wormholes$guardMessage(PokemonBattle battle, CallbackInfo ci) {
        if (!message.getRawMessage().contains("move: Endure|" + Spawns.GUARD_MARKER)) return;
        Component line = Component.literal("A powerful force prevents you from knocking out this Pokemon!").withStyle(ChatFormatting.RED);
        battle.dispatchGo(() -> {
            battle.broadcastChatMessage(line);
            return Unit.INSTANCE;
        });
    }
}

package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

public class NoBlockBreaking {
    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                isHyperspace(level) ? InteractionResult.FAIL : InteractionResult.PASS);

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isHyperspace(level));
    }

    public static boolean isHyperspace(Level level) {
        return level.dimension().equals(Hyperspace.HYPERSPACE);
    }
}

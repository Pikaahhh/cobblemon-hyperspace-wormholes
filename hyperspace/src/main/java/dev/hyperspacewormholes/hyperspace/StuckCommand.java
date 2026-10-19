package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /stuck. If you get stuck in Hyperspace it ends your trip and sends you back where you came from, or to Overworld
 * spawn if you're not on a trip. Recalls your Pokemon first.
 */
public class StuckCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> dispatcher.register(
                Commands.literal("stuck").executes(ctx -> stuck(ctx.getSource().getPlayerOrException()))));
    }

    private static int stuck(ServerPlayer player) {
        if (!Hyperspace.inHyperspace(player)) {
            player.sendSystemMessage(Component.literal("You can only use this inside Hyperspace.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (WorldData.get(player.server).trips.containsKey(player.getUUID())) {
            Trips.leave(player, true);
            return 1;
        }
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        Recall.sentOut(player);
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        return 1;
    }
}

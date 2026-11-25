package dev.hyperspacewormholes.hyperspace;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /stuck ends your trip and sends you back where you came from, or to Overworld spawn if not on one. */
public class StuckCommand {
    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(
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

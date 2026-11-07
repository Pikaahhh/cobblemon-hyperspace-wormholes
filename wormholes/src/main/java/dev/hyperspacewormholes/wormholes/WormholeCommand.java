package dev.hyperspacewormholes.wormholes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations;
import dev.hyperspacewormholes.wormholes.ride.WormholeOrder;
import dev.hyperspacewormholes.wormholes.ride.WormholePokemon;
import dev.hyperspacewormholes.wormholes.ride.WormholeSounds;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * /wormhole: create [destination] (op) opens a wormhole 12 blocks ahead that anyone can use, mounted or not; with no
 * destination it starts an Ultra Warp Ride, with one (a WormholeOrder, e.g. cliff[rings=3,category=legendary]) it takes
 * whoever walks in straight there. clearCooldown (op) clears your ride cooldown; unstuck (anyone) ends the Ultra Warp
 * Ride you're on and takes you back to where it started.
 */
import java.util.Collection;
import java.util.List;
public final class WormholeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wormhole")
                .then(Commands.literal("create").requires(src -> src.hasPermission(2))
                        .executes(ctx -> create(ctx, null))
                        .then(Commands.argument("destination", new WormholeOrder.Argument())
                                .executes(ctx -> create(ctx, ctx.getArgument("destination", WormholeOrder.class)))))
                .then(Commands.literal("clearCooldown").requires(src -> src.hasPermission(2))
                        .executes(WormholeCommand::clearCooldown))
                .then(Commands.literal("pity").requires(src -> src.hasPermission(2))
                        .executes(WormholeCommand::showPity)
                        .then(Commands.literal("set")
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.pity_needed"));
                                    return 0;
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> setPity(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("clear").executes(WormholeCommand::clearPity)))
                .then(Commands.literal("bossCleared").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("cleared", BoolArgumentType.bool())
                                .executes(ctx -> bossCleared(ctx, null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> bossCleared(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("unstuck").executes(WormholeCommand::unstuck)));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, @Nullable WormholeOrder order) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel to = order == null ? null : player.server.getLevel(order.world());
        if (order != null && to == null) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.unknown_destination", order.destination()));
            return 0;
        }
        if (order != null && order.pokemon() != null && !WormholePokemon.known(order.pokemon())) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.unknown_species", order.pokemon()));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 pos = player.position().add(flat.scale(12)).add(0, 1, 0);
        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setRings(order == null ? 0 : order.rings());
        wormhole.setWormholeType(order == null ? WormholeType.BLUE : WormholeDestinations.colourOf(order.world()));
        wormhole.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180f, 0f);
        wormhole.setCommanded(order);
        level.addFreshEntity(wormhole);
        UltraWarpRide.clearRunCooldown(player);
        if (to != null) {
            Component name = UltraWarpRide.dimensionName(to);
            String message = order.ride() ? "hyperspace_wormholes.msg.ride_created" : "hyperspace_wormholes.msg.override_created";
            ctx.getSource().sendSuccess(() -> Component.translatable(message, name), false);
        }
        WormholeSounds.open(level, pos, 0.6f, 1.7f);
        return 1;
    }

    private static int clearCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UltraWarpRide.clearRunCooldown(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.cooldowns_cleared"), false);
        return 1;
    }

    private static int showPity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int pity = ctx.getSource().getPlayerOrException().getAttachedOrElse(Wormholes.WHITE_WITHOUT_NECROZMA, 0);
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.pity", pity), false);
        return pity;
    }
    private static int setPity(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (amount < 0) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.pity_negative"));
            return 0;
        }
        int most = GameplayConfig.get().necrozmaPity;
        if (amount > most) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.pity_high", most));
            return 0;
        }
        player.setAttached(Wormholes.WHITE_WITHOUT_NECROZMA, amount);
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.pity_set", amount), false);
        return amount;
    }
    private static int clearPity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (player.getAttachedOrElse(Wormholes.WHITE_WITHOUT_NECROZMA, 0) == 0) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.pity_none"));
            return 0;
        }
        player.setAttached(Wormholes.WHITE_WITHOUT_NECROZMA, 0);
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.pity_cleared"), false);
        return 1;
    }
    private static int bossCleared(CommandContext<CommandSourceStack> ctx, @Nullable Collection<ServerPlayer> targets) throws CommandSyntaxException {
        boolean cleared = BoolArgumentType.getBool(ctx, "cleared");
        Collection<ServerPlayer> players = targets == null ? List.of(ctx.getSource().getPlayerOrException()) : targets;
        for (ServerPlayer player : players) player.setAttached(Wormholes.NECROZMA_BEATEN, cleared);
        String who = players.size() == 1 ? players.iterator().next().getScoreboardName() : players.size() + " players";
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.boss_cleared", String.valueOf(cleared), who), true);
        return players.size();
    }
    private static int unstuck(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!UltraWarpRide.unstick(player)) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.not_riding"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.unstuck"), false);
        return 1;
    }
}

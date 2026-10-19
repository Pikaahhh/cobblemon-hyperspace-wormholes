package dev.hyperspacewormholes.wormholes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.ride.SkyWormholes;
import dev.hyperspacewormholes.wormholes.ride.UltraBeastWorlds;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.ride.WormholeSounds;
import dev.hyperspacewormholes.wormholes.ride.WormholePokemon;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import dev.hyperspacewormholes.wormholes.world.SwoleTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /wormhole. Open and close wormholes, start and leave rides, clear cooldowns, and test sky wormholes and swole
 * trees.
 */
public final class WormholeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wormhole")
                .then(op("create")
                        .executes(ctx -> create(ctx, null))
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    for (String species : UltraBeastWorlds.HOMES.keySet()) builder.suggest("overrides=" + species);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "options")))))
                .then(op("close")
                        .executes(ctx -> close(ctx, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> close(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(op("ride").executes(WormholeCommand::ride))
                .then(op("sky").executes(WormholeCommand::sky))
                .then(op("tree").executes(WormholeCommand::tree))
                .then(Commands.literal("exit").executes(WormholeCommand::exit))
                .then(op("cooldown")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearCooldowns(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> clearCooldowns(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(src -> src.hasPermission(2));
    }

    private static Vec3 ahead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        return player.position().add(flat.scale(distance));
    }

    private static int clearCooldowns(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UltraWarpRide.clearRunCooldown(player);
            player.removeAttached(Wormholes.NEXT_FLUTE);
            player.getCooldowns().removeCooldown(Flutes.SUN_FLUTE);
            player.getCooldowns().removeCooldown(Flutes.MOON_FLUTE);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.cooldowns_cleared", count), true);
        return count;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, @Nullable String options) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String override = null;
        if (options != null) {
            String option = options.trim().toLowerCase(Locale.ROOT);
            if (!option.startsWith("overrides=")) {
                ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.bad_option", options));
                return 0;
            }
            override = option.substring("overrides=".length()).trim();
            if (override.startsWith("cobblemon:")) override = override.substring("cobblemon:".length());
            if (WormholePokemon.ultraBeastNamed(override, player.getRandom()) == null) {
                ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.unknown_species", override));
                return 0;
            }
        }
        ServerLevel level = player.serverLevel();
        Vec3 pos = ahead(player, 12).add(0, 1, 0);
        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setRings(0);
        wormhole.setWormholeType(WormholeType.BLUE);
        wormhole.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180f, 0f);
        wormhole.setOverride(override);
        level.addFreshEntity(wormhole);
        UltraWarpRide.clearRunCooldown(player);
        if (override != null) {
            String leadsTo = override;
            ctx.getSource().sendSuccess(() -> Component.translatable("hyperspace_wormholes.msg.override_created", leadsTo), false);
        }
        WormholeSounds.open(level, pos, 0.6f, 1.7f);
        return 1;
    }

    private static int close(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        List<WormholeEntity> found = src.getLevel().getEntitiesOfClass(WormholeEntity.class,
                new AABB(src.getPosition(), src.getPosition()).inflate(radius));
        found.forEach(WormholeEntity::discard);
        return found.size();
    }

    private static int ride(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!UltraWarpRide.canEnter(player)) return 0;
        UltraWarpRide.start(player, player.position(), player.getYRot());
        return 1;
    }

    private static int tree(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(ahead(player, 55)));
        int height = SwoleTree.build(level, base, player.getRandom());
        ctx.getSource().sendSuccess(() -> Component.literal("Grew a swole tree " + height + " blocks tall at " + base.toShortString()), false);
        return 1;
    }

    private static int sky(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (SkyWormholes.open(ctx.getSource().getPlayerOrException())) return 1;
        ctx.getSource().sendFailure(Component.literal("No room for a sky wormhole here (it needs ground nearby and open sky above)."));
        return 0;
    }

    private static int exit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!UltraWarpRide.isRiding(player)) {
            ctx.getSource().sendFailure(Component.translatable("hyperspace_wormholes.msg.not_riding"));
            return 0;
        }
        UltraWarpRide.end(player);
        return 1;
    }
}

package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The op command for boss Pokemon: /boss spawn &lt;bossName&gt; puts a boss from BossDefs 4 blocks in front of you facing
 * you, /boss setHP &lt;selector&gt; &lt;percentage&gt; sets its shared HP and /boss despawn &lt;bossUUID&gt; removes it. A
 * selector takes the entity's UUID or name, or the boss you're looking at. The rest is for test games
 * (Wormholes.TEST_COMMANDS): spawnProperties, list, remove, join, reload, defs, hp and autoplay.
 */
public final class BossCommand {
    private BossCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("boss").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .executes(ctx -> fail(ctx, "A boss name is required."))
                        .then(Commands.argument("bossName", StringArgumentType.greedyString())
                                .suggests(BossCommand::suggestBosses)
                                .executes(BossCommand::spawnBoss)))
                .then(Commands.literal("despawn")
                        .executes(ctx -> fail(ctx, "A boss is required."))
                        .then(Commands.argument("bossUUID", EntityArgument.entities()).executes(BossCommand::despawn)))
                .then(Commands.literal("setHP")
                        .executes(ctx -> fail(ctx, "A boss is required."))
                        .then(Commands.argument("selector", EntityArgument.entities())
                                .executes(ctx -> fail(ctx, "A percentage is required."))
                                .then(Commands.argument("percentage", DoubleArgumentType.doubleArg()).executes(BossCommand::setHp))));
        if (Wormholes.TEST_COMMANDS) {
            root.then(Commands.literal("spawnProperties").then(Commands.argument("properties", StringArgumentType.greedyString())
                            .executes(BossCommand::spawn)))
                    .then(Commands.literal("list").executes(BossCommand::list))
                    .then(Commands.literal("remove").executes(ctx -> remove(ctx, false))
                            .then(Commands.literal("all").executes(ctx -> remove(ctx, true))))
                    .then(Commands.literal("join").executes(ctx -> join(ctx, List.of(ctx.getSource().getPlayerOrException())))
                            .then(Commands.argument("players", EntityArgument.players())
                                    .executes(ctx -> join(ctx, EntityArgument.getPlayers(ctx, "players")))))
                    .then(Commands.literal("reload").executes(BossCommand::reload))
                    .then(Commands.literal("defs").executes(BossCommand::defs))
                    .then(Commands.literal("hp").then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                            .executes(BossCommand::hp)))
                    .then(Commands.literal("autoplay").then(Commands.argument("on", BoolArgumentType.bool())
                            .executes(ctx -> autoplay(ctx, null))
                            .then(Commands.argument("except", EntityArgument.player())
                                    .executes(ctx -> autoplay(ctx, EntityArgument.getPlayer(ctx, "except"))))));
        }
        dispatcher.register(root);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestBosses(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BossDefs.ids().stream()
                .map(id -> id.getNamespace().equals(Wormholes.MOD_ID) ? id.getPath() : id.toString()), builder);
    }

    private static int spawnBoss(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return fail(ctx, "This command can only be run by a player.");
        BossDef def = BossDefs.find(StringArgumentType.getString(ctx, "bossName").trim().toLowerCase(Locale.ROOT));
        if (def == null) return fail(ctx, "Could not find a boss that fit that title.");
        double perTrainer = def.hpPerTrainer > 0 ? def.hpPerTrainer : BossConfig.get().hpPerTrainer;
        List<BossDef.Variant> variants = new ArrayList<>(def.variants.values());
        String variant = variants.isEmpty() ? "" : variants.get(player.getRandom().nextInt(variants.size())).properties();
        String properties = String.join(" ", def.spawnSpecies, def.properties, variant).trim();
        return place(ctx, player, def, properties, perTrainer, def.scale > 0 ? def.scale : BossConfig.get().scale);
    }

    private static @Nullable Entity selected(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getOptionalEntities(ctx, name);
        if (entities.size() == 1) return entities.iterator().next();
        fail(ctx, entities.isEmpty() ? "Could not find that entity." : "Only one entity can be selected at a time.");
        return null;
    }

    private static int setHp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double percentage = DoubleArgumentType.getDouble(ctx, "percentage");
        if (percentage < 0) return fail(ctx, "Cannot set HP to a negative percentage.");
        if (percentage > 100) return fail(ctx, "Cannot set HP above 100 percent.");
        Entity entity = selected(ctx, "selector");
        if (entity == null) return 0;
        Boss boss = Bosses.of(entity);
        if (boss == null) return fail(ctx, "That entity is not a boss.");
        boss.setHpFraction(percentage / 100.0);
        ctx.getSource().sendSuccess(() -> Component.literal("Set the boss's HP to " + percentage + "%."), true);
        return 1;
    }

    private static int despawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entity = selected(ctx, "bossUUID");
        if (entity == null) return 0;
        Boss boss = Bosses.of(entity);
        if (boss == null) return fail(ctx, "That entity is not a boss.");
        boss.entity.discard();
        ctx.getSource().sendSuccess(() -> Component.literal("Despawned the boss."), true);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> words = new ArrayList<>(Arrays.asList(StringArgumentType.getString(ctx, "properties").trim().split("\\s+")));
        List<Double> numbers = new ArrayList<>();
        while (words.size() > 1 && numbers.size() < 2 && isNumber(words.getLast())) numbers.addFirst(Double.parseDouble(words.removeLast()));
        BossDef def = words.size() == 1 ? BossDefs.find(words.getFirst()) : null;
        String properties = def != null ? null : String.join(" ", words);
        if (def == null) def = BossDef.empty(Wormholes.id("custom"));
        double perTrainer = !numbers.isEmpty() ? numbers.getFirst() : def.hpPerTrainer > 0 ? def.hpPerTrainer : BossConfig.get().hpPerTrainer;
        float scale = numbers.size() >= 2 ? numbers.get(1).floatValue() : def.scale > 0 ? def.scale : BossConfig.get().scale;
        if (perTrainer <= 0 || scale <= 0) {
            return fail(ctx, "The HP multiplier and scale must be above 0.");
        }
        return place(ctx, player, def, properties, perTrainer, scale);
    }

    private static int place(CommandContext<CommandSourceStack> ctx, ServerPlayer player, BossDef def, @Nullable String properties,
                             double perTrainer, float scale) {
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(look.scale(4));
        float yaw = player.getYRot() + 180f;
        PokemonEntity entity = Bosses.spawn(player.serverLevel(), def, properties, at, yaw, perTrainer, scale, null);
        if (entity == null) return fail(ctx, "That boss could not be spawned.");
        Boss boss = Bosses.of(entity);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned boss " + (boss != null ? boss.describe() : "")), true);
        return 1;
    }

    private static boolean isNumber(String word) {
        try {
            Double.parseDouble(word);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<Boss> bosses = Bosses.all();
        if (bosses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No bosses."), false);
            return 0;
        }
        for (Boss boss : bosses) ctx.getSource().sendSuccess(() -> Component.literal("Boss " + boss.describe()), false);
        return bosses.size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, boolean all) {
        int removed = 0;
        Boss nearest = all ? null : nearest(ctx.getSource().getPosition(), 64);
        for (Boss boss : Bosses.all()) {
            if (!all && boss != nearest) continue;
            boss.entity.discard();
            removed++;
        }
        int count = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " boss" + (count == 1 ? "." : "es.")), true);
        return removed;
    }

    private static int join(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int joined = 0;
        for (ServerPlayer player : players) {
            Boss boss = nearest(player.position(), 128);
            if (boss == null || boss.entity.level() != player.level()) {
                ctx.getSource().sendFailure(Component.literal("No boss near " + player.getName().getString() + "."));
                continue;
            }
            if (boss.join(player, null) instanceof SuccessfulBattleStart) joined++;
        }
        int count = joined;
        ctx.getSource().sendSuccess(() -> Component.literal(count + " joined a boss fight."), false);
        return joined;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        int count = BossDefs.reload(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Loaded " + count + " boss definitions."), true);
        return count;
    }

    private static int defs(CommandContext<CommandSourceStack> ctx) {
        List<String> ids = BossDefs.ids().stream().map(Object::toString).toList();
        ctx.getSource().sendSuccess(() -> Component.literal(ids.isEmpty() ? "No boss definitions." : "Boss definitions: " + String.join(", ", ids)), false);
        return ids.size();
    }

    private static int hp(CommandContext<CommandSourceStack> ctx) {
        Boss boss = nearest(ctx.getSource().getPosition(), 128);
        if (boss == null) {
            ctx.getSource().sendFailure(Component.literal("No boss near."));
            return 0;
        }
        boss.setHpFraction(DoubleArgumentType.getDouble(ctx, "percent") / 100.0);
        ctx.getSource().sendSuccess(() -> Component.literal("Boss " + boss.describe()), true);
        return 1;
    }

    private static int autoplay(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer except) {
        Boss.autoplay = BoolArgumentType.getBool(ctx, "on");
        Boss.autoplayExcept = except == null ? null : except.getUUID();
        ctx.getSource().sendSuccess(() -> Component.literal("Boss autoplay " + (Boss.autoplay ? "on" : "off")
                + (except != null ? " except for " + except.getName().getString() : "") + "."), true);
        return 1;
    }

    private static @Nullable Boss nearest(Vec3 from, double range) {
        Boss best = null;
        double bestDistance = range * range;
        for (Boss boss : Bosses.all()) {
            double d = boss.entity.position().distanceToSqr(from);
            if (d <= bestDistance) {
                best = boss;
                bestDistance = d;
            }
        }
        return best;
    }
}

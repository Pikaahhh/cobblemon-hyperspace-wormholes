package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The op command for boss Pokemon: /boss spawn &lt;bossName&gt; puts a boss from BossDefs 4 blocks in front of you facing
 * you, /boss setHP &lt;selector&gt; &lt;percentage&gt; sets its shared HP and /boss despawn &lt;bossUUID&gt; removes it. A
 * selector takes the entity's UUID or name, or the boss you're looking at.
 */
public final class BossCommand {
    private BossCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("boss").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
                                .then(Commands.argument("percentage", DoubleArgumentType.doubleArg()).executes(BossCommand::setHp)))));
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

}

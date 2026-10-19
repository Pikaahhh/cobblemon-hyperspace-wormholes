package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /hyperspace for ops. Make Hoopa Rings with whatever stars, types, legendary, Mega and odds you want, plus a few
 * other tools for testing trips.
 */
public class HyperspaceCommand {
    private static final String[] BERRIES = {"occa", "passho", "wacan", "rindo", "yache", "chople", "kebia", "shuca",
            "coba", "payapa", "tanga", "charti", "kasib", "haban", "colbur", "babiri", "chilan", "roseli"};
    private static final String[] OPTIONS = {"star=", "cal=", "type=", "legendary=", "mega=", "shiny=", "alpha="};

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> dispatcher.register(
                Commands.literal("hyperspace")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("ring")
                                .executes(ctx -> {
                                    give(ctx.getSource().getPlayerOrException(), new ItemStack(ModItems.HOOPA_RING));
                                    return 1;
                                })
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .suggests(HyperspaceCommand::suggest)
                                        .executes(ctx -> ring(ctx.getSource(), StringArgumentType.getString(ctx, "options")))))
                        .then(Commands.literal("donuts").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (String berry : BERRIES) {
                                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", berry + "_berry");
                                give(player, DonutItem.withBerry(BuiltInRegistries.ITEM.get(id), 1));
                            }
                            return 1;
                        }))
        ));
    }

    private static int ring(CommandSourceStack source, String options) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int stars = 0, seconds = -1, shiny = 1, alpha = 1;
        String type = null, legendary = null, mega = null;
        for (String option : options.trim().split("\\s+")) {
            if (option.isEmpty()) continue;
            int eq = option.indexOf('=');
            String key = eq < 0 ? option : option.substring(0, eq);
            String value = eq < 0 ? "" : option.substring(eq + 1);
            switch (key) {
                case "star", "stars" -> {
                    stars = number(value);
                    if (stars < 0 || stars > 5) return fail(source, "star must be 0 to 5");
                }
                case "cal", "calories" -> {
                    seconds = number(value);
                    if (seconds < 1) return fail(source, "cal must be a whole number of seconds, 1 or more");
                }
                case "shiny" -> {
                    shiny = number(value);
                    if (shiny < 1) return fail(source, "shiny must be a whole number, 1 or more (it multiplies the shiny chance)");
                }
                case "alpha" -> {
                    alpha = number(value);
                    if (alpha < 1) return fail(source, "alpha must be a whole number, 1 or more (it multiplies the alpha chance)");
                }
                case "legendary" -> {
                    String name = value.toLowerCase(Locale.ROOT);
                    if (name.equals("no")) legendary = null;
                    else if (name.equals("yes") || name.equals(DonutBoosts.RANDOM)) legendary = DonutBoosts.RANDOM;
                    else if (PokemonSpecies.getByName(name) != null) legendary = name;
                    else return fail(source, "Unknown Pokemon: " + value + " (use a Pokemon name or random)");
                }
                case "mega" -> {
                    String name = value.toLowerCase(Locale.ROOT);
                    if (name.equals("no")) mega = null;
                    else if (name.equals("yes") || name.equals(DonutBoosts.RANDOM)) mega = DonutBoosts.RANDOM;
                    else if (!Spawns.megaForms(name).isEmpty()) mega = name;
                    else return fail(source, "No Mega for: " + value + " (use e.g. charizard, charizard_x or random)");
                }
                case "type" -> {
                    if (!DonutBoosts.TYPES.contains(value)) return fail(source, "Unknown type: " + value);
                    type = value;
                }
                default -> {
                    return fail(source, "Unknown option: " + key + " (use star=, cal=, type=, legendary=, mega=, shiny=, alpha=)");
                }
            }
        }

        if (seconds < 1) seconds = Math.round(DonutBoosts.BASE_ENERGY * (1 + 0.1f * stars));
        give(player, DonutBoosts.ring(stars, seconds, type, legendary, mega, shiny, alpha));
        return 1;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        int start = input.lastIndexOf(' ') + 1;
        String last = input.substring(start);
        SuggestionsBuilder here = builder.createOffset(builder.getStart() + start);

        if (last.startsWith("type=")) {
            offer(here, "type=", last.substring(5), DonutBoosts.TYPES);
        } else if (last.startsWith("legendary=")) {
            List<String> names = new ArrayList<>(List.of(DonutBoosts.RANDOM, "no"));
            for (Species species : Spawns.legendPool()) names.add(species.getResourceIdentifier().getPath());
            offer(here, "legendary=", last.substring(10).toLowerCase(Locale.ROOT), names);
        } else if (last.startsWith("mega=")) {
            Set<String> names = new LinkedHashSet<>(List.of(DonutBoosts.RANDOM, "no"));
            for (Spawns.MegaForm form : Spawns.megaForms()) {
                names.add(form.species().getResourceIdentifier().getPath());
                names.add(form.name());
            }
            offer(here, "mega=", last.substring(5).toLowerCase(Locale.ROOT), names);
        } else if (last.startsWith("star=")) {
            for (int i = 0; i <= 5; i++) here.suggest("star=" + i);
        } else {
            for (String option : OPTIONS) if (option.startsWith(last) && !input.contains(option)) here.suggest(option);
        }
        return here.buildFuture();
    }

    private static void offer(SuggestionsBuilder here, String key, String typed, Iterable<String> names) {
        for (String name : names) if (name.startsWith(typed)) here.suggest(key + name);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}

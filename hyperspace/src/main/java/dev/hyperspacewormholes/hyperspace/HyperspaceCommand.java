package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.boss.BossDefs;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /hyperspace for ops: createDistortion puts a spatial distortion on the wall in front of you with the given types and
 * stars, a set Legendary or Mega behind it, or the Hoopa Unbound event with boss=hoopa_unbound; clearDistortions removes
 * those within a radius, locateDistortion points to the nearest like /locate, and pity set and clear change your splices
 * without Hoopa. The rest (portal, splicer and a few trip tools) needs -Dhorizons.autotest=true (testCommands).
 */
public class HyperspaceCommand {
    private static final String[] BERRIES = {"occa", "passho", "wacan", "rindo", "yache", "chople", "kebia", "shuca",
            "coba", "payapa", "tanga", "charti", "kasib", "haban", "colbur", "babiri", "chilan", "roseli"};
    private static final String[] OPTIONS = {"star=", "cal=", "type=", "legendary=", "mega=", "shiny=", "alpha="};
    private static final String[] DISTORTION_OPTIONS = {"type=", "star=", "legendary=", "megaPokemon=", "boss="};
    private static final String HOOPA_BOSS = "hoopa_unbound";
    private static final String PLAYERS_ONLY = "This command can only be run by a player.";
    private static final int CLEAR_RADIUS = 128, LOCATE_RANGE = 500;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hyperspace")
                    .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("createDistortion")
                            .executes(ctx -> createDistortion(ctx.getSource(), ""))
                            .then(Commands.argument("options", StringArgumentType.greedyString())
                                    .suggests(HyperspaceCommand::suggestDistortion)
                                    .executes(ctx -> createDistortion(ctx.getSource(), StringArgumentType.getString(ctx, "options")))))
                    .then(Commands.literal("clearDistortions")
                            .executes(ctx -> clearDistortions(ctx.getSource(), CLEAR_RADIUS))
                            .then(Commands.argument("radius", IntegerArgumentType.integer())
                                    .executes(ctx -> clearDistortions(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("locateDistortion").executes(ctx -> locateDistortion(ctx.getSource())))
                    .then(Commands.literal("pity")
                            .then(Commands.literal("set")
                                    .executes(ctx -> fail(ctx.getSource(), "A pity amount is required."))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer())
                                            .executes(ctx -> setPity(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                            .then(Commands.literal("clear").executes(ctx -> clearPity(ctx.getSource()))));
            if (Wormholes.TEST_COMMANDS) testCommands(root);
            dispatcher.register(root);
        });
    }

    /** Only registered when -Dhorizons.autotest=true. */
    private static void testCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root
                        .then(Commands.literal("portal")
                                .executes(ctx -> portal(ctx.getSource(), ""))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .suggests(HyperspaceCommand::suggest)
                                        .executes(ctx -> portal(ctx.getSource(), StringArgumentType.getString(ctx, "options")))))
                        .then(Commands.literal("splicer")
                                .executes(ctx -> {
                                    Tasks.give(ctx.getSource().getPlayerOrException(), new ItemStack(ModItems.DISTORTION_SPLICER));
                                    return 1;
                                })
                                .then(Commands.literal("hoopa").executes(ctx -> {
                                    Tasks.give(ctx.getSource().getPlayerOrException(), DistortionSplicer.hoopaTest());
                                    return 1;
                                }))
                                // forces a Hoopa splice at the nearest natural distortion, however far, and steps straight into it
                                .then(Commands.literal("open").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (!player.level().dimension().equals(Level.OVERWORLD)) return 0;
                                    Distortions.Distortion here = Distortions.nearest(player);
                                    if (here == null || !Distortions.splice(player, here, true)) return 0;
                                    Vec3 base = here.base();
                                    player.teleportTo(base.x, base.y, base.z);
                                    return 1;
                                }))
                                .then(Commands.literal("ready").executes(ctx -> HoopaEvent.ready(ctx.getSource().getPlayerOrException()) ? 1 : 0)))
                        .then(Commands.literal("summon")
                                .then(Commands.argument("pokemon", StringArgumentType.greedyString())
                                        .executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "pokemon")))))
                        .then(Commands.literal("donuts").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (String berry : BERRIES) {
                                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", berry + "_berry");
                                Tasks.give(player, DonutItem.withBerry(BuiltInRegistries.ITEM.get(id), 1));
                            }
                            return 1;
                        }))
                        // rebuilds the island you're on as the Hoopa arena and shows it from above, the seats or the way home's side
                        .then(Commands.literal("arena")
                                .executes(ctx -> arena(ctx.getSource(), 104, 66.5, 180, 33))
                                .then(Commands.literal("seat")
                                        .executes(ctx -> arena(ctx.getSource(), 67, 22.5, 180, 3)))
                                .then(Commands.literal("home")
                                        .executes(ctx -> arena(ctx.getSource(), 68, 12.5, 0, 3))));
    }

    /** Hoopa rings a Pokemon in (HoopaSummon); "dist=<blocks>" anywhere in the text sets how far ahead (default 6). */
    private static int summon(CommandSourceStack source, String text) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        double distance = 6;
        StringBuilder properties = new StringBuilder();
        for (String part : text.trim().split("\\s+")) {
            if (part.startsWith("dist=")) {
                try {
                    distance = Math.max(3, Math.min(20, Double.parseDouble(part.substring(5))));
                } catch (NumberFormatException e) {
                    return fail(source, "Bad dist: " + part);
                }
            } else {
                properties.append(properties.isEmpty() ? "" : " ").append(part);
            }
        }
        String why = HoopaSummon.start(player, properties.toString(), distance);
        if (why != null) return fail(source, why);
        return 1;
    }

    private static int arena(CommandSourceStack source, double y, double z, float yaw, float pitch) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WorldData.Trip trip = WorldData.get(source.getServer()).trips.get(player.getUUID());
        if (trip == null || !Zones.isZone(trip.island)) return fail(source, "Go to Hyperspace first");
        Zones.arena(player.serverLevel(), trip.island);
        BlockPos centre = Zones.centre(trip.island - Zones.BASE);
        player.teleportTo(player.serverLevel(), centre.getX() + 0.5, y, centre.getZ() + z, Set.of(), yaw, pitch);
        return 1;
    }

    private static int createDistortion(CommandSourceStack source, String options) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return fail(source, PLAYERS_ONLY);
        String typeText = null, starText = null, legendary = null, mega = null, boss = null;
        for (String option : options.trim().split("\\s+")) {
            if (option.isEmpty()) continue;
            int eq = option.indexOf('=');
            String value = eq < 0 ? "" : option.substring(eq + 1).toLowerCase(Locale.ROOT);
            switch ((eq < 0 ? option : option.substring(0, eq)).toLowerCase(Locale.ROOT)) {
                case "type", "types" -> typeText = typeText == null ? value : typeText + "," + value;
                case "star", "stars" -> starText = value;
                case "legendary" -> legendary = value;
                case "megapokemon" -> mega = value;
                case "boss" -> boss = value;
                default -> {
                    return fail(source, "That option does not exist. Use type=, star=, legendary=, megaPokemon= or boss=.");
                }
            }
        }
        if (boss != null && (typeText != null || starText != null || legendary != null || mega != null)) {
            return fail(source, "Distortions with a boss specified cannot use any other arguments.");
        }
        if ((typeText != null ? 1 : 0) + (legendary != null ? 1 : 0) + (mega != null ? 1 : 0) > 1) {
            return fail(source, "Distortions can only use one of type/s, legendary or megaPokemon.");
        }
        if (!player.level().dimension().equals(Level.OVERWORLD)) return fail(source, "Distortions can only be created in the Overworld.");
        int stars = starText == null ? 0 : number(starText);
        if (starText != null && (stars < 1 || stars > 5)) return fail(source, "Star rank must be a number from 1 to 5.");

        List<String> types = new ArrayList<>();
        if (typeText != null) {
            for (String type : typeText.split("[,/]+")) {
                if (!DonutBoosts.TYPES.contains(type)) return fail(source, "The type/s provided do not exist or aren't spelt correctly.");
                if (!types.contains(type)) types.add(type);
            }
            if (types.isEmpty()) return fail(source, "The type/s provided do not exist or aren't spelt correctly.");
            if (types.size() > 3) return fail(source, "Distortions can only have up to 3 types.");
        }
        if (legendary != null && !legendary.equals(DonutBoosts.RANDOM)) {
            Species species = species(legendary);
            if (species == null) return fail(source, "The Pokemon provided does not exist or isn't spelt correctly.");
            if (!Spawns.legendary(species)) return fail(source, "Distortions with a Pokemon specified can only use Legendary Pokemon.");
            types = typesOf(species.getTypes());
        }
        if (mega != null && !mega.equals(DonutBoosts.RANDOM)) {
            List<Spawns.MegaForm> forms = Spawns.megaForms(mega);
            if (forms.isEmpty()) {
                return fail(source, species(mega.split("_")[0]) == null ? "The Pokemon provided does not exist or isn't spelt correctly."
                        : "The Pokemon provided does not have a Mega Evolution.");
            }
            types = typesOf(forms.getFirst().form().getTypes());
        }
        if (boss != null && !boss.equals(HOOPA_BOSS)) {
            return fail(source, BossDefs.find(boss) != null ? "That boss cannot be reached through a distortion."
                    : "The boss provided does not exist or isn't spelt correctly.");
        }

        Distortions.Distortion placed = Distortions.place(player, stars, types, legendary, mega, boss != null);
        if (placed == null) return fail(source, "Requires 3x3 area in front of player to place.");
        source.sendSuccess(() -> Component.literal("Created a spatial distortion: "
                + Distortions.describe(placed, null, player.level().getGameTime())), true);
        return 1;
    }

    private static Species species(String name) {
        try {
            return PokemonSpecies.getByName(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> typesOf(Iterable<ElementalType> types) {
        List<String> names = new ArrayList<>();
        for (ElementalType type : types) {
            String name = type.getName().toLowerCase(Locale.ROOT);
            if (DonutBoosts.TYPES.contains(name)) names.add(name);
        }
        return names;
    }

    private static int clearDistortions(CommandSourceStack source, int radius) {
        if (radius < 0) return fail(source, "Cannot clear distortions in a negative radius.");
        int cleared = source.getLevel().dimension().equals(Level.OVERWORLD) ? Distortions.clear(source.getLevel(), source.getPosition(), radius) : 0;
        if (cleared == 0) return fail(source, "There are no distortions nearby.");
        source.sendSuccess(() -> Component.literal("Cleared " + cleared + (cleared == 1 ? " distortion." : " distortions.")), true);
        return cleared;
    }

    private static int locateDistortion(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return fail(source, PLAYERS_ONLY);
        Distortions.Distortion nearest = player.level().dimension().equals(Level.OVERWORLD) ? Distortions.nearest(player) : null;
        if (nearest == null || nearest.distance(player) > LOCATE_RANGE) {
            return fail(source, "There are no distortions within " + LOCATE_RANGE + " blocks of you.");
        }
        int x = nearest.x, y = nearest.y, z = nearest.z;
        Component coordinates = ComponentUtils.wrapInSquareBrackets(Component.literal(x + ", " + y + ", " + z)).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " " + y + " " + z))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip"))));
        long blocks = Math.round(nearest.distance(player));
        source.sendSuccess(() -> Component.literal("The nearest distortion is at ").append(coordinates)
                .append(" (" + blocks + " blocks away)"), false);
        return (int) blocks;
    }

    private static int setPity(CommandSourceStack source, int amount) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return fail(source, PLAYERS_ONLY);
        if (amount < 0) return fail(source, "Cannot set pity to a negative number.");
        if (amount > DistortionSplicer.HOOPA_PITY) return fail(source, "Cannot set pity above " + DistortionSplicer.HOOPA_PITY + ".");
        player.setAttached(Wormholes.SPLICES_WITHOUT_HOOPA, amount);
        source.sendSuccess(() -> Component.literal("Set your pity to " + amount + " (the next splice is sure to be Hoopa from "
                + DistortionSplicer.HOOPA_PITY + ")" + (player.getAttachedOrElse(Wormholes.HOOPA_BEATEN, false)
                ? ", but you've beaten Hoopa so there's no pity." : ".")), false);
        return amount;
    }

    private static int clearPity(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return fail(source, PLAYERS_ONLY);
        if (player.getAttachedOrElse(Wormholes.SPLICES_WITHOUT_HOOPA, 0) == 0) return fail(source, "You have no pity to clear.");
        player.setAttached(Wormholes.SPLICES_WITHOUT_HOOPA, 0);
        source.sendSuccess(() -> Component.literal("Cleared your pity."), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestDistortion(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        int start = input.lastIndexOf(' ') + 1;
        String last = input.substring(start), before = input.substring(0, start);
        SuggestionsBuilder here = builder.createOffset(builder.getStart() + start);

        if (last.startsWith("type=") || last.startsWith("types=")) {
            int comma = Math.max(last.lastIndexOf(','), last.indexOf('='));
            offer(here, last.substring(0, comma + 1), last.substring(comma + 1).toLowerCase(Locale.ROOT), DonutBoosts.TYPES);
        } else if (last.startsWith("legendary=")) {
            List<String> names = new ArrayList<>(List.of(DonutBoosts.RANDOM));
            for (Species species : Spawns.legendPool()) names.add(species.getResourceIdentifier().getPath());
            offer(here, "legendary=", last.substring(10).toLowerCase(Locale.ROOT), names);
        } else if (last.startsWith("megaPokemon=")) {
            Set<String> names = new LinkedHashSet<>(List.of(DonutBoosts.RANDOM));
            names.addAll(megaNames());
            offer(here, "megaPokemon=", last.substring(12).toLowerCase(Locale.ROOT), names);
        } else if (last.startsWith("boss=")) {
            offer(here, "boss=", last.substring(5).toLowerCase(Locale.ROOT), List.of(HOOPA_BOSS));
        } else if (last.startsWith("star=")) {
            for (int i = 1; i <= 5; i++) here.suggest("star=" + i);
        } else if (!before.contains("boss=")) {
            boolean chosen = before.contains("type") || before.contains("legendary=") || before.contains("megaPokemon=");
            for (String option : DISTORTION_OPTIONS) {
                boolean allowed = option.equals("star=") ? !before.contains("star=") : option.equals("boss=") ? before.isEmpty() : !chosen;
                if (allowed && option.startsWith(last)) here.suggest(option);
            }
        }
        return here.buildFuture();
    }

    private static int portal(CommandSourceStack source, String options) throws CommandSyntaxException {
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
        if (player.level().dimension().equals(Hyperspace.HYPERSPACE)) {
            return fail(source, "You cant go to Hyperspace Hyperspace, that isn't a thing!");
        }
        if (!Trips.ready(source.getServer())) return fail(source, "Hyperspace isn't ready yet, try again soon!");
        if (!Portals.grounded(player)) return fail(source, "The portal needs solid ground to open on!");
        if (!Portals.fits(player)) return fail(source, "The portal needs more space to open!");
        if (!Portals.open(player, DonutBoosts.commandDonut(stars, seconds, type, legendary, mega, shiny, alpha))) {
            return fail(source, "Hyperspace is full right now, try again soon!");
        }
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
            names.addAll(megaNames());
            offer(here, "mega=", last.substring(5).toLowerCase(Locale.ROOT), names);
        } else if (last.startsWith("star=")) {
            for (int i = 0; i <= 5; i++) here.suggest("star=" + i);
        } else {
            for (String option : OPTIONS) if (option.startsWith(last) && !input.contains(option)) here.suggest(option);
        }
        return here.buildFuture();
    }

    private static Set<String> megaNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Spawns.MegaForm form : Spawns.megaForms()) {
            names.add(form.species().getResourceIdentifier().getPath());
            names.add(form.name());
        }
        return names;
    }

    private static void offer(SuggestionsBuilder here, String key, String typed, Iterable<String> names) {
        for (String name : names) if (name.startsWith(typed)) here.suggest(key + name);
    }
}

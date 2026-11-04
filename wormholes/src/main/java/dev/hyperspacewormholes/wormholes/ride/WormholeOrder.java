package dev.hyperspacewormholes.wormholes.ride;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Where a /wormhole create wormhole goes and what waits there, written the way vanilla writes block states and selector
 * options: destination[key=value,...], e.g. cliff[rings=3,category=legendary] or ultra_jungle[pokemon=buzzwole]. The
 * destination is an Ultra Beast world by its id or a Wilds world by its short name (WormholeDestinations.named). Keys: rings,
 * pokemon (not together with category), and for the Wilds only category (WormholePokemon.categories()) and ride (true starts
 * an Ultra Warp Ride whose wormholes all lead there). Argument parses it with vanilla's errors and suggests as you type.
 */
public record WormholeOrder(String destination, ResourceKey<Level> world, int rings, @Nullable String category,
                            @Nullable String pokemon, boolean ride) {
    private static final List<String> KEYS = List.of("rings", "category", "pokemon", "ride");
    private static final DynamicCommandExceptionType BAD_RIDE = new DynamicCommandExceptionType(
            value -> Component.translatable("hyperspace_wormholes.msg.bad_ride", value));
    private static final DynamicCommandExceptionType UNKNOWN_DESTINATION = new DynamicCommandExceptionType(
            name -> Component.translatable("hyperspace_wormholes.msg.unknown_destination", name));
    private static final DynamicCommandExceptionType UNKNOWN_KEY = new DynamicCommandExceptionType(
            key -> Component.translatable("hyperspace_wormholes.msg.unknown_option", key));
    private static final DynamicCommandExceptionType REPEATED_KEY = new DynamicCommandExceptionType(
            key -> Component.translatable("hyperspace_wormholes.msg.repeated_option", key));
    private static final DynamicCommandExceptionType BAD_CATEGORY = new DynamicCommandExceptionType(
            value -> Component.translatable("hyperspace_wormholes.msg.unknown_category", value,
                    String.join(", ", WormholePokemon.categories())));
    private static final DynamicCommandExceptionType CATEGORY_ONLY_WILDS = new DynamicCommandExceptionType(
            destination -> Component.translatable("hyperspace_wormholes.msg.category_only_wilds", destination));
    private static final DynamicCommandExceptionType BAD_RINGS = new DynamicCommandExceptionType(
            value -> Component.translatable("hyperspace_wormholes.msg.bad_rings", value, WarpTunnel.GAME_TOP_RINGS));
    private static final SimpleCommandExceptionType POKEMON_AND_CATEGORY = new SimpleCommandExceptionType(
            Component.translatable("hyperspace_wormholes.msg.pokemon_and_category"));
    private static final SimpleCommandExceptionType EXPECTED_VALUE = new SimpleCommandExceptionType(
            Component.translatable("hyperspace_wormholes.msg.expected_value"));
    private static final SimpleCommandExceptionType UNCLOSED = new SimpleCommandExceptionType(
            Component.translatable("hyperspace_wormholes.msg.unclosed_options"));

    public @Nullable String chosenPokemon(RandomSource random, @Nullable ServerPlayer player) {
        if (pokemon != null || category != null) return WormholePokemon.chosen(world, category, pokemon, rings, random, player);
        String beast = UltraBeastWorlds.beastOf(world);
        return beast == null ? null : WormholePokemon.ultraBeastNamed(beast, random);
    }

    public static WormholeOrder parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String destination = reader.readUnquotedString().toLowerCase(Locale.ROOT);
        ResourceKey<Level> world = WormholeDestinations.named(destination);
        if (world == null) throw failAt(reader, start, UNKNOWN_DESTINATION, destination);
        int rings = 0;
        boolean ride = false;
        String category = null, pokemon = null;
        if (reader.canRead() && reader.peek() == '[') {
            reader.skip();
            Set<String> seen = new HashSet<>();
            reader.skipWhitespace();
            while (reader.canRead() && reader.peek() != ']') {
                int at = reader.getCursor();
                String key = reader.readUnquotedString().toLowerCase(Locale.ROOT);
                if (!KEYS.contains(key)) throw failAt(reader, at, UNKNOWN_KEY, key);
                if (!seen.add(key)) throw failAt(reader, at, REPEATED_KEY, key);
                if (key.equals("category") && !WormholeDestinations.isWilds(world)) {
                    throw failAt(reader, at, CATEGORY_ONLY_WILDS, destination);
                }
                reader.skipWhitespace();
                reader.expect('=');
                reader.skipWhitespace();
                int valueAt = reader.getCursor();
                String value = readValue(reader).toLowerCase(Locale.ROOT);
                switch (key) {
                    case "rings" -> {
                        try {
                            rings = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            rings = -1;
                        }
                        if (rings < 0 || rings > WarpTunnel.GAME_TOP_RINGS) throw failAt(reader, valueAt, BAD_RINGS, value);
                    }
                    case "category" -> {
                        if (!WormholePokemon.categories().contains(value)) throw failAt(reader, valueAt, BAD_CATEGORY, value);
                        category = value;
                    }
                    case "ride" -> {
                        if (!value.equals("true") && !value.equals("false")) throw failAt(reader, valueAt, BAD_RIDE, value);
                        ride = value.equals("true");
                    }
                    default -> pokemon = value;
                }
                if (category != null && pokemon != null) {
                    reader.setCursor(at);
                    throw POKEMON_AND_CATEGORY.createWithContext(reader);
                }
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() == ',') {
                    reader.skip();
                    reader.skipWhitespace();
                } else if (!reader.canRead() || reader.peek() != ']') {
                    throw UNCLOSED.createWithContext(reader);
                }
            }
            if (!reader.canRead()) throw UNCLOSED.createWithContext(reader);
            reader.skip();
        }
        return new WormholeOrder(destination, world, rings, category, pokemon, ride);
    }

    private static CommandSyntaxException failAt(StringReader reader, int cursor, DynamicCommandExceptionType type,
                                                 Object value) {
        reader.setCursor(cursor);
        return type.createWithContext(reader, value);
    }

    private static String readValue(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) return reader.readQuotedString();
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ',' && reader.peek() != ']' && reader.peek() != ' ') reader.skip();
        if (reader.getCursor() == start) throw EXPECTED_VALUE.createWithContext(reader);
        return reader.getString().substring(start, reader.getCursor());
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        int open = typed.indexOf('[');
        if (open < 0) {
            for (String name : WormholeDestinations.names()) {
                if (name.startsWith(typed)) {
                    builder.suggest(name);
                    if (name.equals(typed)) builder.suggest(name + "[");
                }
            }
            return builder.buildFuture();
        }
        String inside = typed.substring(open + 1);
        if (inside.contains("]")) return builder.buildFuture();
        int split = inside.lastIndexOf(',') + 1;
        String entry = inside.substring(split).stripLeading();
        int entryAt = open + 1 + inside.length() - entry.length();
        ResourceKey<Level> world = WormholeDestinations.named(typed.substring(0, open));
        boolean wilds = world != null && WormholeDestinations.isWilds(world);
        Set<String> used = new HashSet<>();
        for (String done : inside.substring(0, split).split(",")) {
            int eq = done.indexOf('=');
            if (eq > 0) used.add(done.substring(0, eq).trim());
        }
        int eq = entry.indexOf('=');
        if (eq < 0) {
            SuggestionsBuilder keys = builder.createOffset(builder.getStart() + entryAt);
            for (String key : KEYS) {
                boolean clash = key.equals("pokemon") && used.contains("category")
                        || key.equals("category") && (used.contains("pokemon") || !wilds);
                if (!used.contains(key) && !clash && key.startsWith(entry)) keys.suggest(key + "=");
            }
            if (entry.isEmpty()) keys.suggest("]");
            return keys.buildFuture();
        }
        String key = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).stripLeading();
        SuggestionsBuilder values = builder.createOffset(builder.getStart() + entryAt + entry.length() - value.length());
        List<String> options = new ArrayList<>();
        switch (key) {
            case "rings" -> {
                for (int i = 0; i <= WarpTunnel.GAME_TOP_RINGS; i++) options.add(Integer.toString(i));
            }
            case "category" -> {
                if (wilds) options.addAll(WormholePokemon.categories());
            }
            case "pokemon" -> options.addAll(WormholePokemon.speciesNames());
            case "ride" -> options.addAll(List.of("true", "false"));
            default -> {}
        }
        for (String option : options) {
            if (!option.startsWith(value)) continue;
            values.suggest(option);
            if (option.equals(value)) {
                values.suggest(option + ",");
                values.suggest(option + "]");
            }
        }
        return values.buildFuture();
    }

    public static final class Argument implements ArgumentType<WormholeOrder> {
        @Override
        public WormholeOrder parse(StringReader reader) throws CommandSyntaxException {
            return WormholeOrder.parse(reader);
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            return suggest(builder);
        }

        @Override
        public List<String> getExamples() {
            return List.of("cliff", "ultra_deep_sea", "cliff[rings=3,category=legendary]", "ultra_jungle[pokemon=buzzwole]",
                    "ultra_crater[ride=true]");
        }
    }
}

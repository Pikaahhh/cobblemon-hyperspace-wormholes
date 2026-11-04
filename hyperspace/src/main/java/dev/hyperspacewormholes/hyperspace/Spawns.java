package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Wild Pokemon in Hyperspace. Each trip gets a pool (3 common, 2 uncommon, 2 rare and 1 ultra-rare from the hyperspace
 * tag, picked by type and stars), and they spawn around you with alpha herds, levels and shiny odds from the ring.
 * Also handles the legendary and Mega bosses and their announcements, clears leftovers, and blocks Cobblemon's normal
 * spawns there. Herd followers only path where they fit.
 */
public class Spawns {
    public static int maxNearby = 9;
    private static final int[] POOL_SIZES = {3, 2, 2, 1};
    private static final int[] BAND_LOW = {0, 341, 461, 541};
    private static final int[] BAND_HIGH = {340, 460, 540, Integer.MAX_VALUE};
    private static final int MAX_POOLS = 64;
    public static final String HERD = "hyperspace_herd";
    public static float herdChance = 0.1f;
    public static int megaLevel = 200;
    public static float bossShinyChance = 0.25f;
    private static final Set<String> NOT_SPAWNED = Set.of("cosmog", "cosmoem", "solgaleo", "lunala");
    private static final Set<String> MONUMENT_LEGENDS = Set.of(
            "articuno", "zapdos", "moltres", "mew", "celebi", "entei", "raikou", "suicune", "lugia", "hooh",
            "latias", "latios", "regirock", "regice", "registeel", "uxie", "mesprit", "azelf", "dialga", "palkia",
            "giratina", "heatran", "regigigas", "cresselia", "darkrai", "victini", "cobalion", "terrakion",
            "virizion", "reshiram", "zekrom", "kyurem", "keldeo", "meloetta", "enamorus", "hoopa", "xerneas",
            "yveltal", "cosmog", "marshadow", "zacian", "zamazenta", "eternatus", "regieleki", "regidrago",
            "glastrier", "spectrier", "calyrex", "wochien", "chienpao", "tinglu", "chiyu");
    public static final String GUARD = "hyperspaceguard";
    public static final String GUARD_MARKER = "[hyperspace_wormholes]";

    public static final String BOSS = "hyperspace_boss";
    public static final String BORN = "hyperspace_born";
    private static final Set<String> MEGA_MODS = Set.of("mega_showdown", "zamega");
    private static final Set<String> EXTRA_MEGAS = Set.of("rayquaza mega");
    private static Set<String> allowedMegas = Set.of();
    private static final List<Entity> leftovers = new ArrayList<>();
    public static final String MEGA = "hyperspace_mega";
    public static final String OWNER = "hyperspace_owner";
    public static final String REVERT = "hyperspace_revert";
    public static final Set<String> PARADOX_LEGENDS = Set.of("walkingwake", "ironleaves", "gougingfire", "ragingbolt",
            "ironboulder", "ironcrown");
    private static final Map<String, String[]> STRONGEST = Map.ofEntries(
            Map.entry("mewtwo", new String[]{"mega_evolution=mega_x", "mega_evolution=mega_y", "mega_evolution=none"}),
            Map.entry("rayquaza", new String[]{"mega_evolution=mega", "mega_evolution=none"}),
            Map.entry("latias", new String[]{"mega_evolution=mega", "mega_evolution=none"}),
            Map.entry("latios", new String[]{"mega_evolution=mega", "mega_evolution=none"}),
            Map.entry("groudon", new String[]{"reversion_state=primal", "reversion_state=standard"}),
            Map.entry("kyogre", new String[]{"reversion_state=primal", "reversion_state=standard"}),
            Map.entry("zacian", new String[]{"behemoth_warrior=crowned", "behemoth_warrior=hero"}),
            Map.entry("zamazenta", new String[]{"behemoth_warrior=crowned", "behemoth_warrior=hero"}),
            Map.entry("dialga", new String[]{"orb_forme=origin", "orb_forme=altered"}),
            Map.entry("palkia", new String[]{"orb_forme=origin", "orb_forme=altered"}),
            Map.entry("giratina", new String[]{"orb_forme=origin", "orb_forme=altered"}));

    private static List<Species> tagged = List.of();
    private static List<Species> legendTagged = List.of();
    private static final Map<String, List<List<Species>>> pools = new HashMap<>();
    private static final Map<Integer, Integer> sweeping = new HashMap<>();

    private record Echo(ServerPlayer player, Holder<SoundEvent> sound, int at, float volume) {
    }

    private static final float[] ECHOES = {0.55f, 0.3f, 0.15f};
    private static final List<Echo> echoes = new ArrayList<>();
    private static final Map<UUID, Integer> arrived = new HashMap<>();

    public static void register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.NORMAL, event -> {
            if (event.getEntity().level().dimension().equals(Hyperspace.HYPERSPACE)) event.cancel();
            return Unit.INSTANCE;
        });
        ServerTickEvents.END_SERVER_TICK.register(Spawns::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server.getResourceManager()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> load(server.getResourceManager()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!level.dimension().equals(Hyperspace.HYPERSPACE) || !(entity instanceof PokemonEntity pokemon) || !pokemon.getPokemon().isWild()) return;
            // the Hoopa event's boss and the legendaries it rings in aren't island spawns (no BORN stamp)
            if (HoopaEvent.ours(entity)) return;
            if (Zones.leftover(entity.blockPosition(), pokemon.getPokemon().getPersistentData().getLong(BORN))) leftovers.add(entity);
        });
    }

    private static void tick(MinecraftServer server) {
        for (Entity entity : leftovers) entity.discard();
        leftovers.clear();

        int now = server.getTickCount();
        echoes.removeIf(echo -> {
            if (now < echo.at()) return false;
            ServerPlayer player = echo.player();
            if (!player.isRemoved()) player.connection.send(new ClientboundSoundPacket(echo.sound(), SoundSource.NEUTRAL,
                    player.getX(), player.getY(), player.getZ(), echo.volume(), 1f, player.getRandom().nextLong()));
            return true;
        });

        ServerLevel hyperspace = server.getLevel(Hyperspace.HYPERSPACE);
        if (hyperspace == null) return;

        if (now % 5 == 0) {
            sweeping.entrySet().removeIf(entry -> {
                AABB box = new AABB(Zones.middle(entry.getKey())).inflate(48, 64, 48);
                for (Entity entity : hyperspace.getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) entity.discard();
                entry.setValue(entry.getValue() - 5);
                return entry.getValue() <= 0;
            });
        }

        WorldData data = WorldData.get(server);
        if (now % 10 == 0) {
            for (PokemonEntity entity : wild(hyperspace)) {
                boolean keep = isBoss(entity.getPokemon()) ? ownerHere(hyperspace, data, entity)
                        : hyperspace.getNearestPlayer(entity, 64) != null;
                if (!keep) entity.discard();
            }
            clearLooseItems(hyperspace);
        }
        if (now % 40 == 0) OtherPlayersLegendaries.sync(hyperspace);
        if (now % 20 != 0) return;
        followHerds(hyperspace);

        arrived.keySet().removeIf(id -> hyperspace.getPlayerByUUID(id) == null || !data.trips.containsKey(id));
        for (ServerPlayer player : hyperspace.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip != null && HoopaEvent.held(trip.island)) continue;
            if (trip != null && trip.megaPending && spawnMega(hyperspace, player, trip)) {
                trip.megaPending = false;
                data.setDirty();
            }
            if (trip != null && !trip.legendaryDone && trip.legendaryPending) spawnLegendary(hyperspace, data, trip);
            if (ownsBoss(hyperspace, player) || trip != null && !trip.legendaryDone && trip.legendaryPending) continue;
            if (trip != null && now - arrived.computeIfAbsent(player.getUUID(), id -> now) < 60) continue;

            int nearby = (int) wild(hyperspace).stream().filter(entity -> entity.distanceToSqr(player) < 32 * 32).count();
            for (int i = 0; i < 2 && nearby < (trip == null ? maxNearby : trip.powers.nearbyCap(maxNearby)); i++) {
                if (spawn(hyperspace, player, trip)) nearby++;
            }
        }
    }

    public static void sweep(int island) {
        sweeping.put(island, 100);
    }

    private static void clearLooseItems(ServerLevel level) {
        List<Entity> loose = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if ((entity instanceof ItemEntity || entity instanceof ExperienceOrb) && level.getNearestPlayer(entity, 64) == null) loose.add(entity);
        }
        for (Entity entity : loose) entity.discard();
    }

    public static boolean isBoss(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        return data != null && data.getBoolean(BOSS);
    }

    public static String megaBoss(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        return data != null && data.contains(MEGA) ? data.getString(MEGA) : null;
    }

    public static UUID owner(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        return data != null && data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
    }

    private static boolean ownerHere(ServerLevel level, WorldData data, PokemonEntity entity) {
        UUID owner = owner(entity.getPokemon());
        WorldData.Trip trip = owner == null ? null : data.trips.get(owner);
        if (trip == null || level.getPlayerByUUID(owner) == null) return false;
        int island = Zones.islandAt(entity.blockPosition());
        return island < 0 || island == trip.island;
    }

    private static boolean ownsBoss(ServerLevel level, ServerPlayer player) {
        return wild(level).stream()
                .anyMatch(entity -> isBoss(entity.getPokemon()) && player.getUUID().equals(owner(entity.getPokemon())));
    }

    static List<? extends PokemonEntity> wild(ServerLevel level) {
        return level.getEntities(CobblemonEntities.POKEMON, entity -> entity.getPokemon().isWild());
    }

    private static boolean spawn(ServerLevel level, ServerPlayer player, WorldData.Trip trip) {
        RandomSource random = level.getRandom();
        BlockPos pos = findSpot(level, player.blockPosition(), random);
        if (pos == null) return false;
        List<List<Species>> buckets = pool(trip);
        Species species = pickSpecies(buckets, rarity(trip), random);
        if (species == null) return false;
        if ((buckets.get(2).contains(species) || buckets.get(3).contains(species)) && random.nextFloat() < herdChance) {
            return spawnHerd(level, species, pos, trip, random);
        }
        Pokemon pokemon = species.create(level(stars(trip), random));
        if (random.nextFloat() < alphaChance(trip)) pokemon.setAlpha(true);
        rollShiny(pokemon, trip, random);
        rollSize(pokemon, trip, random);
        return place(level, pokemon, pos, random) != null;
    }

    private static boolean spawnHerd(ServerLevel level, Species species, BlockPos pos, WorldData.Trip trip, RandomSource random) {
        List<Species> kinds = herdKinds(species);
        Pokemon leader = species.create(DonutBoosts.maxLevel(stars(trip)));
        leader.setAlpha(true);
        leader.setScaleModifier(leaderScale(species, kinds));
        PokemonProperties.Companion.parse("held_item=cobblemon:" + species.getPrimaryType().getName().toLowerCase(Locale.ROOT) + "_gem").apply(leader);
        rollShiny(leader, trip, random);
        PokemonEntity head = place(level, leader, pos, random);
        if (head == null) return false;
        int followers = 3 + random.nextInt(3);
        for (int i = 0; i < followers; i++) {
            BlockPos spot = findSpot(level, pos, random);
            if (spot == null) continue;
            Pokemon follower = kinds.get(random.nextInt(kinds.size())).create(level(stars(trip), random));
            rollShiny(follower, trip, random);
            rollSize(follower, trip, random);
            follower.getPersistentData().putUUID(HERD, head.getUUID());
            place(level, follower, spot, random);
        }
        return true;
    }

    private static int level(int stars, RandomSource random) {
        int min = DonutBoosts.minLevel(stars);
        return min + random.nextInt(DonutBoosts.maxLevel(stars) - min + 1);
    }

    static List<Species> herdKinds(Species species) {
        List<Species> kinds = new ArrayList<>();
        Species current = species;
        while (current.getPreEvolution() != null) {
            Species pre = current.getPreEvolution().getSpecies();
            if (pre == null || !pre.getImplemented() || kinds.contains(pre) || pre == species) break;
            kinds.add(pre);
            current = pre;
        }
        if (kinds.isEmpty()) kinds.add(species);
        return kinds;
    }

    private static float leaderScale(Species species, List<Species> kinds) {
        float biggest = 0;
        for (Species kind : kinds) biggest = Math.max(biggest, size(kind));
        return Math.max(1.25f, 1.3f * biggest / Math.max(0.1f, size(species)));
    }

    private static float size(Species species) {
        return Math.max(species.getHitbox().width(), species.getHitbox().height()) * species.getBaseScale();
    }

    private static void followHerds(ServerLevel level) {
        for (PokemonEntity entity : wild(level)) {
            CompoundTag data = entity.getPokemon().getPersistentData();
            if (!data.hasUUID(HERD) || entity.isBattling()) continue;
            Entity leader = level.getEntity(data.getUUID(HERD));
            if (!(leader instanceof PokemonEntity) || !leader.isAlive()) {
                data.remove(HERD);
                continue;
            }
            if (entity.distanceToSqr(leader) > 8 * 8) {
                entity.addTag(QuickBattles.FIT_PATH);
                entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(leader, 0.35f, 3));
            }
        }
    }

    private static void rollShiny(Pokemon pokemon, WorldData.Trip trip, RandomSource random) {
        int rolls = trip == null ? 1 : Math.max(1, trip.shiny);
        if (random.nextFloat() < rolls / Math.max(1f, Cobblemon.INSTANCE.getConfig().getShinyRate())) pokemon.setShiny(true);
    }

    private static void rollSize(Pokemon pokemon, WorldData.Trip trip, RandomSource random) {
        if (trip != null) trip.powers.size(pokemon, random);
    }

    public static float alphaChance(WorldData.Trip trip) {
        return trip == null ? DonutBoosts.ALPHA_CHANCE : trip.alphaChance;
    }

    private static void spawnLegendary(ServerLevel level, WorldData data, WorldData.Trip starterTrip) {
        RandomSource random = level.getRandom();
        Species species = starterTrip.legendarySpecies != null ? PokemonSpecies.getByName(starterTrip.legendarySpecies) : null;
        if (species == null) species = pickLegendary(types(starterTrip), random);
        if (species == null) return;
        List<BlockPos> taken = new ArrayList<>(wild(level).stream().filter(entity -> isBoss(entity.getPokemon()))
                .map(Entity::blockPosition).toList());
        for (ServerPlayer player : level.players()) {
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip == null || trip.island != starterTrip.island || trip.legendaryDone) continue;
            BlockPos pos = Zones.isZone(trip.island) ? middleSpot(level, Zones.middle(trip.island), taken) : null;
            if (pos == null) pos = findSpot(level, player.blockPosition(), random);
            if (pos == null) continue;
            Pokemon pokemon = boss(species, trip, player, random);
            if (place(level, pokemon, pos, random) == null) continue;
            taken.add(pos);
            trip.legendaryDone = true;
            trip.legendaryPending = false;
            data.setDirty();
            announce(level, player, species, random);
            OtherPlayersLegendaries.sync(level);
        }
    }

    private static Pokemon boss(Species species, WorldData.Trip trip, ServerPlayer owner, RandomSource random) {
        Pokemon pokemon = species.create(level(stars(trip), random));
        PokemonProperties.Companion.parse("ability=" + GUARD).apply(pokemon);
        String[] forms = STRONGEST.get(species.getResourceIdentifier().getPath());
        if (forms != null) {
            PokemonProperties.Companion.parse(forms[RandomSource.create(trip.poolSeed).nextInt(forms.length - 1)]).apply(pokemon);
            pokemon.getPersistentData().putString(REVERT, forms[forms.length - 1]);
        }
        pokemon.getPersistentData().putBoolean(BOSS, true);
        pokemon.getPersistentData().putUUID(OWNER, owner.getUUID());
        if (random.nextFloat() < Math.max(alphaChance(trip), trip.legendaryAlpha)) pokemon.setAlpha(true);
        pokemon.setScaleModifier(2f);
        rollShiny(pokemon, trip, random);
        if (random.nextFloat() < Math.max(bossShinyChance, trip.legendaryShiny)) pokemon.setShiny(true);
        pokemon.setCurrentHealth(pokemon.getMaxHealth());
        return pokemon;
    }

    private static void announce(ServerLevel level, ServerPlayer player, Species species, RandomSource random) {
        ResourceLocation cry = species.getNationalPokedexNumber() > 1025
                ? ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon." + species.getResourceIdentifier().getPath() + ".cry")
                : Hyperspace.id("cry." + species.getNationalPokedexNumber());
        Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(cry));
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, player.getX(), player.getY(), player.getZ(), 1f, 1f, random.nextLong()));
        for (int i = 0; i < ECHOES.length; i++) {
            echoes.add(new Echo(player, sound, level.getServer().getTickCount() + 8 * (i + 1), ECHOES[i]));
        }
    }

    public record MegaForm(Species species, FormData form, String aspect) {
        public String name() {
            return species.getResourceIdentifier().getPath() + (aspect.equals("mega") ? "" : aspect.substring(4));
        }
    }

    public static List<MegaForm> megaForms() {
        List<MegaForm> megas = new ArrayList<>();
        for (Species s : PokemonSpecies.getImplemented()) {
            for (FormData form : s.getForms()) {
                for (String aspect : form.getAspects()) {
                    if (aspect.startsWith("mega") && megaAllowed(s, aspect)) megas.add(new MegaForm(s, form, aspect));
                }
            }
        }
        return megas;
    }

    private static void loadMegas(ResourceManager resources) {
        Set<String> found = new HashSet<>(EXTRA_MEGAS);
        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.listResourceStacks("mega_showdown/mega", path -> path.getPath().endsWith(".json")).entrySet()) {
            Resource file = null;
            for (Resource resource : entry.getValue()) {
                for (String mod : MEGA_MODS) if (resource.sourcePackId().contains(mod)) file = resource;
            }
            if (file == null) continue;
            try (Reader reader = file.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement apply : json.getAsJsonObject("aspect_conditions").getAsJsonObject("apply").getAsJsonArray("aspects")) {
                    String aspect = apply.getAsString();
                    if (!aspect.startsWith("mega_evolution=")) continue;
                    for (JsonElement name : json.getAsJsonArray("pokemons")) {
                        found.add(name.getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + " " + aspect.substring("mega_evolution=".length()));
                    }
                }
            } catch (Exception e) {
                Hyperspace.LOGGER.warn("Couldn't read mega file {}", entry.getKey(), e);
            }
        }
        if (found.size() == EXTRA_MEGAS.size()) {
            Hyperspace.LOGGER.warn("No Mega Showdown or ZA Mega mega files found, Hyperspace Megas aren't filtered");
            found.clear();
        }
        allowedMegas = found;
    }

    public static List<String> readTag(ResourceManager resources, String path) {
        List<String> names = new ArrayList<>();
        for (Resource resource : resources.getResourceStack(Hyperspace.id(path))) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("replace") && json.get("replace").getAsBoolean()) names.clear();
                for (JsonElement value : json.getAsJsonArray("values")) names.add(value.getAsString());
            } catch (Exception e) {
                Hyperspace.LOGGER.warn("Couldn't read the Pokemon tag {} from {}", path, resource.sourcePackId(), e);
            }
        }
        return names;
    }

    private static List<Species> species(List<String> names) {
        List<Species> found = new ArrayList<>();
        for (String name : names) {
            Species species = name.contains(":") ? PokemonSpecies.getByIdentifier(ResourceLocation.parse(name)) : PokemonSpecies.getByName(name);
            if (species != null && species.getImplemented() && !found.contains(species)) found.add(species);
        }
        return found;
    }

    private static void load(ResourceManager resources) {
        loadMegas(resources);
        tagged = species(readTag(resources, "tags/pokemon/hyperspace.json"));
        pools.clear();
        legendTagged = species(readTag(resources, "tags/pokemon/legendaries.json"));
    }

    private static boolean megaAllowed(Species species, String aspect) {
        return allowedMegas.isEmpty() || allowedMegas.contains(species.getResourceIdentifier().getPath() + " " + aspect);
    }

    public static List<MegaForm> megaForms(String name) {
        return megaForms().stream()
                .filter(mega -> mega.name().equals(name) || mega.species().getResourceIdentifier().getPath().equals(name)).toList();
    }

    private static boolean spawnMega(ServerLevel level, ServerPlayer player, WorldData.Trip trip) {
        RandomSource random = level.getRandom();
        BlockPos pos = findSpot(level, player.blockPosition(), random);
        if (pos == null) return false;
        List<MegaForm> any = trip.megaSpecies != null ? megaForms(trip.megaSpecies) : megaForms();
        List<MegaForm> matching = any.stream().filter(mega -> hasType(mega.form().getTypes(), types(trip))).toList();
        List<MegaForm> pool = trip.megaSpecies != null || matching.isEmpty() ? any : matching;
        if (pool.isEmpty()) return true;

        MegaForm pick = pool.get(random.nextInt(pool.size()));
        Pokemon pokemon = pick.species().create(megaLevel);
        PokemonProperties.Companion.parse("mega_evolution=" + pick.aspect()).apply(pokemon);
        PokemonProperties.Companion.parse("ability=hyperspacemega").apply(pokemon);
        UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
        pokemon.getPersistentData().putString(MEGA, pick.aspect());
        pokemon.setScaleModifier(2f);
        pokemon.setCurrentHealth(pokemon.getMaxHealth());
        if (place(level, pokemon, pos, random) == null) return false;
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) announce(level, near, pick.species(), random);
        }
        return true;
    }

    private static PokemonEntity place(ServerLevel level, Pokemon pokemon, BlockPos pos, RandomSource random) {
        pokemon.getPersistentData().putLong(BORN, level.getGameTime());
        PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static Species pickSpecies(List<List<Species>> buckets, int rarity, RandomSource random) {
        float[] weights = weights(rarity);
        float roll = random.nextFloat();
        int bucket = 0;
        while (bucket < 3 && roll >= weights[bucket]) {
            roll -= weights[bucket];
            bucket++;
        }
        int found = nonEmpty(buckets, bucket);
        if (found < 0) return null;
        List<Species> pool = buckets.get(found);
        return pool.get(random.nextInt(pool.size()));
    }

    private static int nonEmpty(List<List<Species>> buckets, int bucket) {
        for (int i = bucket; i >= 0; i--) if (!buckets.get(i).isEmpty()) return i;
        for (int i = 0; i < 4; i++) if (!buckets.get(i).isEmpty()) return i;
        return -1;
    }

    public static List<String> types(WorldData.Trip trip) {
        return trip == null || trip.types.isEmpty() ? List.of("ghost") : trip.types;
    }

    public static int rarity(WorldData.Trip trip) {
        return trip == null ? 0 : trip.rarity;
    }

    public static boolean isLegend(Species species) {
        if (PARADOX_LEGENDS.contains(species.getResourceIdentifier().getPath())) return true;
        Set<String> labels = species.getLabels();
        return labels.contains("legendary") && !labels.contains("ultra_beast") && !labels.contains("paradox");
    }

    public static boolean legendary(Species species) {
        return legendTagged.contains(species) || species.getLabels().contains("legendary") || species.getLabels().contains("mythical");
    }

    public static List<Species> legendPool() {
        List<Species> pool = new ArrayList<>();
        boolean monuments = FabricLoader.getInstance().isModLoaded("legendarymonuments");
        for (Species species : PokemonSpecies.getImplemented()) {
            String path = species.getResourceIdentifier().getPath();
            if (!legendTagged.contains(species) || NOT_SPAWNED.contains(path) || monuments && MONUMENT_LEGENDS.contains(path)) continue;
            if (!species.getBehaviour().getMoving().getWalk().getCanWalk() && !species.getBehaviour().getMoving().getFly().getCanFly()) continue;
            pool.add(species);
        }
        return pool;
    }

    public static Species pickLegendary(List<String> types, RandomSource random) {
        List<Species> any = legendPool();
        List<Species> matching = any.stream().filter(species -> hasType(species.getTypes(), types)).toList();
        List<Species> pool = matching.isEmpty() ? any : matching;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    public static int stars(WorldData.Trip trip) {
        return trip == null ? 1 : Math.max(1, trip.stars);
    }

    public static List<List<Species>> pool(WorldData.Trip trip) {
        int stars = stars(trip);
        int tier = stars <= 1 ? 0 : stars == 2 ? 1 : 2;
        List<String> types = types(trip);
        long seed = trip == null ? 0 : trip.poolSeed;
        if (pools.size() > MAX_POOLS) pools.clear();
        return pools.computeIfAbsent(tier + " " + types + " " + seed, key -> pick(types, tier, seed));
    }

    private static List<List<Species>> pick(List<String> types, int tier, long seed) {
        RandomSource random = RandomSource.create(seed);
        List<List<Species>> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) buckets.add(new ArrayList<>());
        fill(buckets, candidates(species -> hasType(species.getTypes(), types) && fits(species, tier), buckets), random);
        if (missing(buckets)) fill(buckets, candidates(species -> hasType(species.getTypes(), types) && !species.getLabels().contains("baby"), buckets), random);
        if (missing(buckets)) fill(buckets, candidates(species -> fits(species, tier), buckets), random);
        return buckets;
    }

    private static List<Species> candidates(Predicate<Species> test, List<List<Species>> buckets) {
        List<Species> found = new ArrayList<>();
        for (Species species : tagged) {
            if (!species.getBehaviour().getMoving().getWalk().getCanWalk() || !test.test(species)) continue;
            if (buckets.stream().noneMatch(bucket -> bucket.contains(species))) found.add(species);
        }
        found.sort(Comparator.comparing(Species::getName));
        return found;
    }

    private static void fill(List<List<Species>> buckets, List<Species> left, RandomSource random) {
        for (int b = 0; b < 4; b++) {
            int band = b;
            List<Species> natives = new ArrayList<>(left.stream().filter(species -> band(bst(species)) == band).toList());
            while (buckets.get(b).size() < POOL_SIZES[b] && !natives.isEmpty()) {
                Species chosen = natives.remove(random.nextInt(natives.size()));
                left.remove(chosen);
                buckets.get(b).add(chosen);
            }
        }
        for (int b = 3; b >= 0; b--) {
            int band = b;
            while (buckets.get(b).size() < POOL_SIZES[b] && !left.isEmpty()) {
                Species closest = left.stream().min(Comparator.comparingInt(species -> distance(bst(species), band))).orElseThrow();
                left.remove(closest);
                buckets.get(b).add(closest);
            }
        }
    }

    private static boolean missing(List<List<Species>> buckets) {
        return IntStream.range(0, 4).anyMatch(b -> buckets.get(b).size() < POOL_SIZES[b]);
    }

    private static int band(int total) {
        for (int b = 0; b < 3; b++) {
            if (total <= BAND_HIGH[b]) return b;
        }
        return 3;
    }

    private static int distance(int total, int band) {
        if (total < BAND_LOW[band]) return BAND_LOW[band] - total;
        return total > BAND_HIGH[band] ? total - BAND_HIGH[band] : 0;
    }

    private static List<Species> chain(Species species) {
        List<Species> chain = new ArrayList<>();
        chain.add(species);
        Species current = species;
        while (current.getPreEvolution() != null) {
            Species pre = current.getPreEvolution().getSpecies();
            if (pre == null || !pre.getImplemented() || pre.getLabels().contains("baby") || chain.contains(pre)) break;
            chain.add(0, pre);
            current = pre;
        }
        return chain;
    }

    private static boolean fits(Species species, int tier) {
        if (species.getLabels().contains("baby")) return false;
        int depth = chain(species).size() - 1;
        if (tier == 0) return depth == 0;
        if (tier == 1) return depth == 1 || depth == 0 && !canEvolve(species);
        return !canEvolve(species);
    }

    private static float[] weights(int rarity) {
        float[] weights = {60f, 28f * (float) Math.pow(1.2, rarity), 10f * (float) Math.pow(1.35, rarity), 2f * (float) Math.pow(1.5, rarity)};
        float total = weights[0] + weights[1] + weights[2] + weights[3];
        for (int i = 0; i < 4; i++) weights[i] /= total;
        return weights;
    }

    public static float[] bucketChances(List<List<Species>> buckets, int rarity) {
        float[] chances = weights(rarity);
        float[] result = new float[4];
        for (int i = 0; i < 4; i++) {
            int target = nonEmpty(buckets, i);
            if (target >= 0) result[target] += chances[i];
        }
        return result;
    }

    private static int bst(Species species) {
        return species.getBaseStats().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static boolean canEvolve(Species species) {
        for (Evolution evolution : species.getEvolutions()) {
            String name = evolution.getResult().getSpecies();
            if (name == null) continue;
            Species next = name.contains(":")
                    ? PokemonSpecies.getByIdentifier(ResourceLocation.parse(name))
                    : PokemonSpecies.getByName(name.toLowerCase());
            if (next != null && next.getImplemented()) return true;
        }
        return false;
    }

    private static boolean hasType(Iterable<ElementalType> speciesTypes, List<String> types) {
        for (ElementalType type : speciesTypes) {
            if (types.contains(type.getName().toLowerCase())) return true;
        }
        return false;
    }

    private static BlockPos findSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = random.nextInt(25) - 12;
            int dz = random.nextInt(25) - 12;
            if (dx * dx + dz * dz < 16) continue;
            for (int dy = 6; dy >= -8; dy--) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (standable(level, pos)) return pos;
            }
        }
        return null;
    }

    private static BlockPos middleSpot(ServerLevel level, BlockPos middle, List<BlockPos> taken) {
        for (int r = 0; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 8; dy >= -8; dy--) {
                        BlockPos pos = middle.offset(dx, dy, dz);
                        if (taken.stream().anyMatch(other -> other.distSqr(pos) < 5 * 5)) break;
                        if (standable(level, pos)) return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && isOpen(level, pos) && isOpen(level, pos.above());
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}

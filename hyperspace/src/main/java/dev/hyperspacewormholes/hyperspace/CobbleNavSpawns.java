package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePositionType;
import com.cobblemon.mod.common.pokemon.Species;
import com.metacontent.cobblenav.spawndata.BlockConditions;
import com.metacontent.cobblenav.spawndata.CheckedSpawnData;
import com.metacontent.cobblenav.spawndata.SpawnData;
import com.metacontent.cobblenav.spawndata.resultdata.SpawnResultData;
import com.metacontent.cobblenav.utils.WeightedBucket;
import kotlin.Pair;
import kotlin.ranges.IntRange;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Builds CobbleNav's spawn list from your trip's pool while you're in Hyperspace: each rarity's species and chance,
 * plus everything in the boss bucket at the alpha chance. A boss that can bring a herd shows up as a herd row with its
 * followers either side, like Cobblemon's own herds do.
 */
public class CobbleNavSpawns {
    private static final List<String> BUCKETS = List.of("common", "uncommon", "rare", "ultra-rare");
    private static final String ALPHA_BUCKET = "boss";

    public static Pair<WeightedBucket, List<CheckedSpawnData>> check(ServerPlayer player, String bucketName) {
        if (!player.level().dimension().equals(Hyperspace.HYPERSPACE)) return null;
        boolean alpha = bucketName.equals(ALPHA_BUCKET);
        int index = alpha ? 0 : BUCKETS.indexOf(bucketName);
        if (index < 0) return new Pair<>(new WeightedBucket(bucketName, 0f), List.of());

        WorldData.Trip trip = WorldData.get(player.server).trips.get(player.getUUID());
        int stars = Spawns.stars(trip);
        List<List<Species>> buckets = Spawns.pool(trip);
        IntRange levels = new IntRange(DonutBoosts.minLevel(stars), DonutBoosts.maxLevel(stars));
        SpawnablePositionType<?> grounded = SpawnablePosition.Companion.getByName("grounded");
        List<Species> pool = alpha ? buckets.stream().flatMap(List::stream).distinct().toList() : buckets.get(index);
        List<CheckedSpawnData> list = new ArrayList<>();
        for (Species species : pool) {
            SpawnDetail detail;
            if (alpha && (buckets.get(2).contains(species) || buckets.get(3).contains(species))) {
                PokemonHerdSpawnDetail herd = new PokemonHerdSpawnDetail();
                List<PokemonHerdSpawnDetail.Herdable> members = new ArrayList<>();
                members.add(member(species, true));
                List<Species> kinds = Spawns.herdKinds(species);
                for (int i = 0; i < Math.max(2, kinds.size()); i++) members.add(member(kinds.get(i % kinds.size()), false));
                herd.setHerdablePokemon(members);
                herd.setLevelRange(levels);
                detail = herd;
            } else {
                PokemonSpawnDetail single = new PokemonSpawnDetail();
                single.setPokemon(PokemonProperties.Companion.parse("species=" + species.getResourceIdentifier()));
                single.setLevelRange(levels);
                detail = single;
            }
            detail.setId("hyperspace-" + species.getResourceIdentifier().getPath());
            detail.setBucket(bucketName);
            if (grounded != null) detail.setSpawnablePositionType(grounded);

            SpawnResultData result = SpawnResultData.Companion.fromDetail(detail, player);
            if (result == null) continue;
            SpawnData data = new SpawnData(detail.getId(), result, "grounded", bucketName, 1f, null,
                    List.of(), List.of(), new BlockConditions(new HashSet<>()), new BlockConditions(new HashSet<>()));
            list.add(new CheckedSpawnData(data, 100f / pool.size()));
        }
        float chance = alpha ? Spawns.alphaChance(trip) : Spawns.bucketChances(buckets, Spawns.rarity(trip))[index];
        return new Pair<>(new WeightedBucket(bucketName, chance), list);
    }

    private static PokemonHerdSpawnDetail.Herdable member(Species species, boolean leader) {
        PokemonHerdSpawnDetail.Herdable member = new PokemonHerdSpawnDetail.Herdable();
        member.setPokemon(PokemonProperties.Companion.parse("species=" + species.getResourceIdentifier()));
        member.setLeader(leader);
        member.setFollower(!leader);
        return member;
    }
}

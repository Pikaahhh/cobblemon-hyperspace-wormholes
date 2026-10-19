package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hyperspace's saved data, so everyone's current trip (island, ring, pool seed, tasks and progress) survives a
 * restart.
 */
public class WorldData extends SavedData {
    public final Map<UUID, Trip> trips = new HashMap<>();
    public final Map<Integer, Zones.Zone> zones = new HashMap<>();
    public final Map<Integer, CompoundTag> tasks = new HashMap<>();

    public static class Trip {
        ResourceKey<Level> dim;
        double x, y, z;
        float yaw, pitch;
        int ticksLeft, rarity, shiny, stars;
        List<String> types = new ArrayList<>();
        boolean megaPending, legendaryPending, legendaryDone;
        String megaSpecies, legendarySpecies;
        float legendaryChance, alphaChance = DonutBoosts.ALPHA_CHANCE, legendaryShiny, legendaryAlpha;
        long poolSeed;
        int island = -1;
    }

    public static WorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(WorldData::new, WorldData::load, null), Hyperspace.MOD_ID);
    }

    private static WorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldData data = new WorldData();
        ListTag list = tag.getList("trips", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Trip trip = new Trip();
            trip.dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
            trip.x = t.getDouble("x");
            trip.y = t.getDouble("y");
            trip.z = t.getDouble("z");
            trip.yaw = t.getFloat("yaw");
            trip.pitch = t.getFloat("pitch");
            trip.ticksLeft = t.getInt("ticks");
            ListTag types = t.getList("types", Tag.TAG_STRING);
            for (int j = 0; j < types.size(); j++) trip.types.add(types.getString(j));
            trip.rarity = t.getInt("rarity");
            trip.shiny = t.contains("shiny") ? t.getInt("shiny") : DonutBoosts.shinyRolls(trip.rarity);
            trip.stars = t.getInt("stars");
            trip.megaPending = t.getBoolean("mega");
            trip.megaSpecies = t.contains("megaSpecies") ? t.getString("megaSpecies") : null;
            trip.legendaryPending = t.getBoolean("legendary");
            trip.legendarySpecies = t.contains("legendSpecies") ? t.getString("legendSpecies") : null;
            trip.legendaryDone = t.getBoolean("legendDone");
            trip.legendaryChance = t.contains("legendChance") ? t.getFloat("legendChance") : trip.stars >= 5 ? DonutBoosts.LEGENDARY_CHANCE : 0f;
            trip.alphaChance = t.contains("alpha") ? t.getFloat("alpha") : DonutBoosts.ALPHA_CHANCE;
            trip.poolSeed = t.getLong("poolSeed");
            trip.legendaryShiny = t.getFloat("legendShiny");
            trip.legendaryAlpha = t.getFloat("legendAlpha");
            trip.island = t.contains("island") ? t.getInt("island") : -1;
            data.trips.put(t.getUUID("player"), trip);
        }

        ListTag zones = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zones.size(); i++) {
            CompoundTag t = zones.getCompound(i);
            data.zones.put(t.getInt("slot"), Zones.Zone.load(t));
        }
        CompoundTag tasks = tag.getCompound("tasks");
        for (String key : tasks.getAllKeys()) data.tasks.put(Integer.parseInt(key), tasks.getCompound(key));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Trip> entry : trips.entrySet()) {
            Trip trip = entry.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("player", entry.getKey());
            t.putString("dim", trip.dim.location().toString());
            t.putDouble("x", trip.x);
            t.putDouble("y", trip.y);
            t.putDouble("z", trip.z);
            t.putFloat("yaw", trip.yaw);
            t.putFloat("pitch", trip.pitch);
            t.putInt("ticks", trip.ticksLeft);
            ListTag types = new ListTag();
            for (String type : trip.types) types.add(StringTag.valueOf(type));
            t.put("types", types);
            t.putInt("rarity", trip.rarity);
            t.putInt("shiny", trip.shiny);
            t.putInt("stars", trip.stars);
            t.putBoolean("mega", trip.megaPending);
            if (trip.megaSpecies != null) t.putString("megaSpecies", trip.megaSpecies);
            t.putBoolean("legendary", trip.legendaryPending);
            if (trip.legendarySpecies != null) t.putString("legendSpecies", trip.legendarySpecies);
            t.putBoolean("legendDone", trip.legendaryDone);
            t.putFloat("legendChance", trip.legendaryChance);
            t.putFloat("alpha", trip.alphaChance);
            t.putLong("poolSeed", trip.poolSeed);
            t.putFloat("legendShiny", trip.legendaryShiny);
            t.putFloat("legendAlpha", trip.legendaryAlpha);
            t.putInt("island", trip.island);
            list.add(t);
        }
        tag.put("trips", list);

        ListTag zoneList = new ListTag();
        for (Map.Entry<Integer, Zones.Zone> entry : zones.entrySet()) {
            CompoundTag t = entry.getValue().save();
            t.putInt("slot", entry.getKey());
            zoneList.add(t);
        }
        tag.put("zones", zoneList);
        CompoundTag taskTag = new CompoundTag();
        for (Map.Entry<Integer, CompoundTag> entry : tasks.entrySet()) taskTag.put(String.valueOf(entry.getKey()), entry.getValue());
        tag.put("tasks", taskTag);
        return tag;
    }
}
